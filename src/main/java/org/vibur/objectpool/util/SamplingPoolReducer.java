/**
 * Copyright 2013 Simeon Malchev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vibur.objectpool.util;

import org.vibur.objectpool.BasePool;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.vibur.objectpool.util.ArgumentValidation.forbidIllegalArgument;

/**
 * A sampling pool reducer util, which is awakened up a given number of times during
 * a predefined period of time, and checks whether the number of available
 * allocated objects in the object pool needs to be reduced.
 *
 * <p>This pool reducer will <b>not</b> bring the number of allocated on the pool
 * objects to less than the pool {@code initial} size.
 *
 * <p>This pool reducer creates one daemon service thread which will be started when
 * the reducer's {@link #start()} method is called, and will be alive until the
 * {@link #terminate()} method is called or until the calling application exits.
 *
 * <p>Note that if an exception is thrown by the overridable
 * {@link #afterReduce(int, int, Throwable)} method hook, it will terminate the
 * SamplingPoolReducer, including the reducer's background daemon thread.
 *
 * @author Simeon Malchev
 */
public class SamplingPoolReducer implements ThreadedPoolReducer {

    private final BasePool pool;
    private final long sleepNanoTime;
    private final int samples;

    private final Thread reducerThread;

    protected static final double MAX_REDUCTION_FRACTION = 0.2;
    protected int minRemainingCreated;

    /**
     * Creates a new {@link SamplingPoolReducer} with the given {@link BasePool} and
     * {@code timeInterval} settings. The created pool reducer is not started and needs to be
     * explicitly started via calling the {@link #start()} method.
     *
     * @param pool the pool that is to be reduced if necessary
     * @param timeInterval the time period after which the {@link SamplingPoolReducer} will try to
     *                     possibly reduce the number of created but unused objects in the
     *                     given {@code pool}
     * @param unit the time unit of the {@code timeInterval} argument
     * @param samples how many times the {@link SamplingPoolReducer} will wake up during the given
     *                {@code timeInterval} period in order to sample various information from
     *                the given {@code pool}
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code timeInterval <= 0 || samples <= 0}
     * @throws NullPointerException if one of the following holds:<br>
     *         {@code pool == null || unit == null}
     */
    public SamplingPoolReducer(BasePool pool, long timeInterval, TimeUnit unit, int samples) {
        forbidIllegalArgument(timeInterval <= 0, "timeInterval");
        forbidIllegalArgument(samples <= 0, "samples");

        this.sleepNanoTime = unit.toNanos(timeInterval) / samples;
        forbidIllegalArgument(sleepNanoTime == 0, "sleepNanoTime");

        this.pool = requireNonNull(pool);
        this.samples = samples;

        this.reducerThread = new Thread(new PoolReducerRunnable());
    }

    @Override
    public void start() {
        reducerThread.setName(getThreadName());
        reducerThread.setDaemon(true);
        reducerThread.setPriority(Thread.MAX_PRIORITY - 2);
        reducerThread.start();
    }

    protected String getThreadName() {
        return reducerThread.getName();
    }

    private class PoolReducerRunnable implements Runnable {
        @Override
        public void run() {
            var sample = 1;
            minRemainingCreated = Integer.MAX_VALUE;
            for (;;) {
                try {
                    NANOSECONDS.sleep(sleepNanoTime);
                    samplePool();
                    if (sample++ % samples == 0) {
                        reducePool();
                        sample = 1;
                        minRemainingCreated = Integer.MAX_VALUE;
                    }
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    protected void samplePool() {
        var remainingCreated = pool.remainingCreated();
        minRemainingCreated = Math.min(minRemainingCreated, remainingCreated);
    }

    protected void reducePool() {
        var reduction = calculateReduction();

        var reduced = -1;
        Throwable thrown = null;
        try {
            reduced = pool.reduceCreatedBy(reduction, false);
        } catch (Throwable t) { // equivalent to catching "RuntimeException | Error", however, better for Kotlin interoperability
            thrown = t;
        } finally {
            afterReduce(reduction, reduced, thrown);
        }
    }

    /**
     * Calculates the number of currently allocated on the pool elements that needs to be destroyed/deallocated,
     * as a result of the stats collected during the just finished observational time period.
     * The number of remaining allocated on the pool objects will <b>not</b> fall below the pool {@code initial}
     * size as a result of this reduction.
     *
     * @return the calculated reduction number
     */
    protected int calculateReduction() {
        var createdTotal = pool.createdTotal();
        var maxReduction = (int) Math.ceil(createdTotal * MAX_REDUCTION_FRACTION);
        var reduction = Math.min(minRemainingCreated, maxReduction);
        var bottomThreshold = createdTotal - pool.initialSize();
        reduction = Math.min(reduction, bottomThreshold);
        return Math.max(reduction, 0);
    }

    /**
     * An after reduce pool hook. The default implementation will {@code terminate()} this pool reducer
     * if {@code thrown != null}. Note that if this method throws an exception, this
     * will terminate the pool reducer, too.
     *
     * @param reduction the intended reduction number
     * @param reduced the number of objects which were successfully removed/destroyed from the pool
     * @param thrown a thrown during the pool reduction exception if any.
     */
    protected void afterReduce(int reduction, int reduced, Throwable thrown) {
        if (thrown != null) {
            terminate();
        }
    }

    @Override
    public Thread.State getState() {
        return reducerThread.getState();
    }

    @Override
    public void terminate() {
        reducerThread.interrupt();
    }
}
