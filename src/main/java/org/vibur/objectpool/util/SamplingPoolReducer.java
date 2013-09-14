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
 * <p><strong>Important</strong> specific to be mentioned is that if an exception is thrown
 * during the pool reduction, which could be in this case a {@code RuntimeException} or an
 * {@code Error}, the default implementation of the overridable
 * {@link #afterReduce(int, int, Throwable)} method will simply rethrow the exception, which
 * will in turn terminate the reducer's background daemon thread.
 *
 * @author Simeon Malchev
 */
public class SamplingPoolReducer implements ThreadedPoolReducer {

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
     */
    public SamplingPoolReducer(BasePoolService poolService, long timeInterval, TimeUnit unit, int samples) {
        if (poolService == null || timeInterval <= 0 || unit == null || samples < 0)
            throw new IllegalArgumentException();

        this.poolService = poolService;
        this.timeInterval = timeInterval;
        this.unit = unit;
        this.samples = samples + 1;
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
        private static final double MAX_REDUCTION_FRACTION = 0.2;
        private int minRemainingCreated = Integer.MAX_VALUE;

        public void run() {
            int iter = 1;
            while (!terminated) {
                try {
                    unit.sleep(sleepTimeout);
                    if (iter % samples > 0) {
                        samplePool();
                        iter++;
                    } else {
                        reducePool();
                        iter = 1;
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }

        private void samplePool() {
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
                } catch (RuntimeException x) {
                    thrown = x;
                } catch (Error x) {
                    thrown = x;
                } finally {
                    try {
                        afterReduce(reduction, reduced, thrown);
                    } catch (Throwable throwable) {
                        terminate();
                    }
                }
            }
        }

        private int calculateReduction() {
            int createdTotal = poolService.createdTotal();
            int maxReduction = (int) (createdTotal * MAX_REDUCTION_FRACTION);
            int bottomReduction = createdTotal - poolService.initialSize();
            int reduction = Math.min(minRemainingCreated, maxReduction);
            reduction = Math.min(reduction, bottomReduction);
            return Math.max(reduction, 0);
        }
    }

    /**
     * An after reduce pool hook. The default implementation will just rethrow the throwable
     * if any, and this will in turn terminate the SamplingPoolReducer.
     *
     * @param reduction the intended reduction
     * @param reduced the number of objects removed/destroyed from the pool
     * @param thrown a thrown exception if any (a RuntimeException or an Error)
     */
    protected void afterReduce(int reduction, int reduced, Throwable thrown) throws Throwable {
        if (thrown != null)
            throw thrown;
    }

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
