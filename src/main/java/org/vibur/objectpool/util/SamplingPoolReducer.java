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

import org.vibur.objectpool.BasePoolService;

import java.util.concurrent.TimeUnit;

/**
 * A sampling pool reducer util, which is wakening up a given number of times during
 * a predefined period of time, and checks whether the number of available
 * allocated objects in the object pool needs to be reduced.
 *
 * <p>This pool reducer creates one daemon service thread which will be started when
 * the reducer's {@link #start()} method is called, and will be alive until the
 * {@link #terminate()} method is called or until the calling application exits.
 *
 * <p><strong>It is important</strong> to be noted that if a RuntimeException or an Error is thrown
 * during the overridable {@link #afterReduce(int, int, Throwable)} method hook execution, it
 * will terminate this SamplingPoolReducer, including the reducer's background daemon thread.
 *
 * @author Simeon Malchev
 */
public class SamplingPoolReducer implements ThreadedPoolReducer {

    private static final double MAX_REDUCTION_FRACTION = 0.2;
    private int minRemainingCreated;

    private final BasePoolService poolService;
    private final long timeInterval;
    private final long sleepTimeout;
    private final TimeUnit unit;
    private final int samples;

    private final Thread reducerThread;
    private volatile boolean terminated = false;

    /**
     * Creates a new {@link SamplingPoolReducer} with the given {@link BasePoolService} and
     * {@code timeInterval} settings. The created pool reducer is not started and needs to be
     * explicitly started via call to {@link #start()}.
     *
     * @param poolService the pool service which is to be reduced if necessary
     * @param timeInterval the time period after which the {@link SamplingPoolReducer} will try to
     *                     possibly reduce the number of created but unused elements in the
     *                     given {@code poolService}
     * @param unit the time unit of the {@code timeInterval} argument
     * @param samples how many times the {@link SamplingPoolReducer} will wake up during the given
     *                {@code timeInterval} period in order to sample various information from
     *                the given {@code poolService}
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code poolService == null || timeInterval <= 0 || unit == null || samples <= 0}
     */
    public SamplingPoolReducer(BasePoolService poolService, long timeInterval, TimeUnit unit, int samples) {
        if (poolService == null || timeInterval <= 0 || unit == null || samples <= 0)
            throw new IllegalArgumentException();

        this.poolService = poolService;
        this.timeInterval = timeInterval;
        this.unit = unit;
        this.samples = samples;
        this.sleepTimeout = timeInterval / this.samples;

        this.reducerThread = new Thread(new PoolReducerRunnable());
        this.reducerThread.setName(toString());
        this.reducerThread.setDaemon(true);
        this.reducerThread.setPriority(Thread.MAX_PRIORITY - 2);
    }

    /** {@inheritDoc} */
    public void start() {
        reducerThread.start();
    }

    private class PoolReducerRunnable implements Runnable {
        public void run() {
            int sample = 1;
            minRemainingCreated = Integer.MAX_VALUE;
            while (!terminated) {
                try {
                    unit.sleep(sleepTimeout);
                    samplePool();
                    if (sample++ % samples == 0) {
                        reducePool();
                        sample = 1;
                        minRemainingCreated = Integer.MAX_VALUE;
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    protected void samplePool() {
        int remainingCreated = poolService.remainingCreated();
        minRemainingCreated = Math.min(minRemainingCreated, remainingCreated);
    }

    private void reducePool() {
        int reduction = calculateReduction();
        if (reduction > 0) {
            int reduced = -1;
            Throwable thrown = null;
            try {
                reduced = poolService.reduceCreated(reduction, false);
            } catch (RuntimeException e) {
                thrown = e;
            } catch (Error e) {
                thrown = e;
            } finally {
                try {
                    afterReduce(reduction, reduced, thrown);
                } catch (Throwable t) {
                    terminate();
                }
            }
        }
    }

    protected int calculateReduction() {
        int createdTotal = poolService.createdTotal();
        int maxReduction = (int) (createdTotal * MAX_REDUCTION_FRACTION);
        int reduction = Math.min(minRemainingCreated, maxReduction);
        int bottomThreshold = createdTotal - poolService.initialSize();
        reduction = Math.min(reduction, bottomThreshold);
        return Math.max(reduction, 0);
    }

    /**
     * An after reduce pool hook. The default implementation does nothing. Note that if this
     * method throws a RuntimeException or an Error, it will terminate this SamplingPoolReducer.
     *
     * @param reduction the intended reduction
     * @param reduced the number of objects removed/destroyed from the pool
     * @param thrown a thrown during the pool reduction exception if any (a RuntimeException or an Error)
     */
    protected void afterReduce(int reduction, int reduced, Throwable thrown) { }

    /** {@inheritDoc} */
    public Thread.State getState() {
        return reducerThread.getState();
    }

    /** {@inheritDoc} */
    public void terminate() {
        terminated = true;
        reducerThread.interrupt();
    }
}
