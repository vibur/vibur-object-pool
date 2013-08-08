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

package vibur.objectpool.util;

import vibur.objectpool.BasePoolService;

import java.util.concurrent.TimeUnit;

/**
 * An allocated objects reducer util, which is wakening up when an
 * unit/timeout period of time expires, and checks whether the number of available
 * allocated objects in the object pool needs to be reduced.
 *
 * <p>The exact pool reduction logic is provided via an instance of {@link Reducer}.
 * This pool reducer creates one daemon service thread which will be started when
 * the pool's {@link #start()} method is called, and will be alive until the
 * {@link #terminate()} method is called or until the calling application exits.

 * @author Simeon Malchev
 */
public class PoolReducer {

    private final BasePoolService poolService;
    private final Reducer reducer;
    private final long timeout;
    private final TimeUnit unit;
    private final Thread reducerThread;

    private volatile boolean terminated = false;

    /**
     * Creates a new {@link PoolReducer} with the given {@link BasePoolService} and
     * timeout settings. The created pool reducer is not started and needs to be explicitly
     * started via call to {@link #start()}.
     *
     * @param poolService the pool service which is tobe reduced if necessary
     * @param reducer provides the calculation logic for how many elements (at most)
     *                to be removed from the monitored pool service
     * @param timeout the time periods after which the {@link PoolReducer} will wake up
     * @param unit the time unit of the {@code timeout} argument
     */
    public PoolReducer(BasePoolService poolService, Reducer reducer,
                       long timeout, TimeUnit unit) {
        if (poolService == null || reducer == null || timeout <= 0 || unit == null)
            throw new IllegalArgumentException();

        this.poolService = poolService;
        this.reducer = reducer;
        this.timeout = timeout;
        this.unit = unit;

        this.reducerThread = new Thread(new PoolReducerRunnable());
        this.reducerThread.setName(toString());
        this.reducerThread.setDaemon(true);
        this.reducerThread.setPriority(Thread.MAX_PRIORITY - 2);
    }

    /**
     * Starts this pool reducer, which starts its underlying daemon thread.
     *
     * @exception IllegalThreadStateException if this pool reducer is started more then once
     */
    public void start() {
        reducerThread.start();
    }

    private class PoolReducerRunnable implements Runnable {
        public void run() {
            while (!terminated) {
                try {
                    unit.sleep(timeout);

                    int reduction = reducer.reduceBy(poolService);
                    if (reduction > 0) {
                        int reduced = -1;
                        Throwable thrown = null;
                        try {
                            reduced = poolService.reduceCreated(reduction);
                        } catch (RuntimeException x) {
                            thrown = x; throw x;
                        } catch (Error x) {
                            thrown = x; throw x;
                        } finally {
                            afterReduce(reduction, reduced, thrown);
                        }
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * An after reduce pool hook. The default implementation does nothing.
     *
     * @param reduction the intended reduction
     * @param reduced the number of objects removed/destroyed from the pool
     * @param thrown a thrown exception if any
     */
    protected void afterReduce(int reduction, int reduced, Throwable thrown) { }

    /**
     * Tests if this pool reducer is alive. A pool reducer is alive if it has
     * been started and has not yet been terminated, more precisely if its
     * underlying daemon thread has not yet died.
     *
     * @return true if terminated, false otherwise
     */
    public boolean isAlive() {
        return reducerThread.isAlive();
    }

    /**
     * Terminates this pool reducer, which terminates its underlying daemon thread.
     * Once terminated the pool reducer cannot be more revived.
     */
    public void terminate() {
        terminated = true;
        reducerThread.interrupt();
    }
}
