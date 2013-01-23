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

package vibur.object_pool;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An implementation of a <i>non-validating</i> object pool based on a {@link ConcurrentLinkedQueue}
 * and a {@link Semaphore}. This object pool does <b>not</b> implement any validation of whether the
 * currently restored object has been taken before that from the object pool, or whether this object
 * is currently in taken state.
 *
 * <p>This object pool provides support for fairness with regards to the waiting taker's threads.
 * The creation of new objects and their lifecycle are controlled by a supplied during the
 * object pool's creation time {@link PoolObjectFactory}.
 *
 * <p>This object pool has also support for an automated shrinking (reduction) of the number of
 * allocated on the object pool objects. Note that the shrinking does <b>never</b> reduce the
 * {@link #createdTotal()} to less than the  pool {@link #initialSize()}.
 *
 * @see ConcurrentHolderLinkedPool
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public class ConcurrentLinkedPool<T> extends AbstractBasePoolService
        implements NonValidatingPoolService<T> {

    private final PoolObjectFactory<T> poolObjectFactory;

    private final Semaphore takeSemaphore;
    private final Queue<T> available;

    private final int initialSize;
    private final AtomicInteger maxSize;
    private final AtomicInteger createdTotal;

    private final long timeout;
    private final TimeUnit unit;
    private final Thread reducerThread;

    private final AtomicLong takenCount = new AtomicLong(0);

    private volatile boolean reducerTerminated = false;
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * Creates a new {@code ConcurrentLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     * and no auto-shrinking.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects
     * @param fair              the object pool's fairness setting with regards to waiting threads
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}<br>
     * @throws NullPointerException if {@code poolObjectFactory} is null
     */
    public ConcurrentLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                int initialSize, int maxSize, boolean fair) {
        this(poolObjectFactory, initialSize, maxSize, fair, 0, null, null);
    }

    /**
     * Creates a new {@code ConcurrentLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     *  and auto-shrinking parameters.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects
     * @param fair              the object pool's fairness setting with regards to waiting threads
     * @param timeout           the amount of time for which to count the number of taken
     *                          objects from the object pool. Set to {@code 0} to disable the
     *                          auto-shrinking
     * @param unit              the time unit of the {@code timeout} argument
     * @param poolReducer       the object pool reducer
     * @throws IllegalArgumentException if the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}<br>
     * @throws NullPointerException if {@code poolObjectFactory} is null or if
     * (timeout > 0 && (unit == null || poolReducer == null))
     */
    public ConcurrentLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                int initialSize, int maxSize, boolean fair,
                                long timeout, TimeUnit unit, PoolReducer poolReducer) {
        if (initialSize < 0 || maxSize < 1 || maxSize < initialSize)
            throw new IllegalArgumentException();
        if (poolObjectFactory == null
                || (timeout > 0 && (unit == null || poolReducer == null)))
            throw new NullPointerException();

        this.poolObjectFactory = poolObjectFactory;
        takeSemaphore = new Semaphore(maxSize, fair);

        available = new ConcurrentLinkedQueue<T>();
        for (int i = 0; i < initialSize; i++) {
            available.add(poolObjectFactory.create());
        }

        this.initialSize = initialSize;
        this.maxSize = new AtomicInteger(maxSize);
        createdTotal = new AtomicInteger(initialSize);

        this.timeout = timeout;
        this.unit = unit;
        if (timeout > 0) {
            reducerThread = new Thread(new PoolReducerThread(poolReducer));
            reducerThread.setName(PoolReducerThread.class.getName());
            reducerThread.setDaemon(true);
            reducerThread.setPriority(Thread.MAX_PRIORITY - 2);
            reducerThread.start();
        } else {
            reducerThread = null;
        }
    }

    // todo move this as an external class in the util package
    /**
     * The allocated objects reducer thread, which is wakening up when an
     * unit/timeout period of time expires, to check whether the number of available
     * allocated objects in the object pool needs to be reduced.
     */
    private class PoolReducerThread implements Runnable {
        private final PoolReducer poolReducer;

        private PoolReducerThread(PoolReducer poolReducer) {
            this.poolReducer = poolReducer;
        }

        @Override
        public void run() {
            while (!reducerTerminated) {
                try {
                    unit.sleep(timeout);

                    int reduction = poolReducer.reduceBy(ConcurrentLinkedPool.this);
                    if (reduction > 0) {
                        try {
                            reduceCreated(reduction);
                        }
                        catch (RuntimeException ignored) { }
                        catch (Error ignored) { }
                    }
                } catch (InterruptedException ignored) { }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public T take() {
        try {
            takeSemaphore.acquire();
            return newTarget();
        }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public T takeUninterruptibly() {
        takeSemaphore.acquireUninterruptibly();
        return newTarget();
    }

    /** {@inheritDoc} */
    @Override
    public T tryTake(long timeout, TimeUnit unit) {
        try {
            if (!takeSemaphore.tryAcquire(timeout, unit))
                return null;

            return newTarget();
        }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public T tryTake() {
        if (!takeSemaphore.tryAcquire())
            return null;

        return newTarget();
    }

    private T newTarget() {
        if (isTerminated()) {
            takeSemaphore.release();
            return null;
        }

        T target = available.poll();
        target = readyToTake(target);
        takenCount.incrementAndGet();
        return target;
    }

    /** {@inheritDoc} */
    @Override
    public void restore(T target) {
        if (target == null) throw new NullPointerException();
        if (isTerminated())
            return;

        target = readyToRestore(target);
        available.add(target);
        takeSemaphore.release();
    }

    private T readyToTake(T target) {
        try {
            if (target == null) {
                createdTotal.incrementAndGet();
                target = poolObjectFactory.create();
            } else if (!poolObjectFactory.readyToTake(target)) {
                poolObjectFactory.destroy(target);
                target = poolObjectFactory.create();
            }
            return target;
        }
        catch (RuntimeException e) { recoverInnerState(1); throw e; }
        catch (Error e) { recoverInnerState(1); throw e; }
    }

    private T readyToRestore(T target) {
        try {
            if (!poolObjectFactory.readyToRestore(target)) {
                poolObjectFactory.destroy(target);
                target = poolObjectFactory.create();
            }
            return target;
        }
        catch (RuntimeException e) { recoverInnerState(1); throw e; }
        catch (Error e) { recoverInnerState(1); throw e; }
    }

    private void recoverInnerState(int permits) {
        createdTotal.addAndGet(-permits);
        takeSemaphore.release(permits);
    }


    /** {@inheritDoc} */
    @Override
    public int createdTotal() {
        return createdTotal.get();
    }

    /** {@inheritDoc} */
    @Override
    public int remainingCapacity() {
        return isTerminated() ? 0 : takeSemaphore.availablePermits();
    }

    /** {@inheritDoc} */
    @Override
    public int initialSize() {
        return initialSize;
    }

    /** {@inheritDoc} */
    @Override
    public int maxSize() {
        return maxSize.get();
    }


    /** {@inheritDoc} */
    @Override
    public int reduceCreated(int reduction) {
        return doReduceCreated(reduction, false);
    }

    private int doReduceCreated(int reduction, boolean ignoreInitialSize) {
        if (reduction < 0)
            throw new IllegalArgumentException();

        int cnt = 0;
        for (; cnt < reduction; cnt++) {
            if (createdTotal.decrementAndGet() < initialSize && !ignoreInitialSize) {
                createdTotal.incrementAndGet();
                break;
            }
            T target = available.poll();
            if (target == null) {
                createdTotal.incrementAndGet();
                break;
            }
            poolObjectFactory.destroy(target);
        }
        return cnt;
    }


    /** {@inheritDoc} */
    @Override
    public void terminate() {
        if (terminated.getAndSet(true))
            return;

        reducerTerminated = true;
        if (reducerThread != null)
            reducerThread.interrupt();

        // best effort to unblock any waiting on the takeSemaphore threads
        takeSemaphore.release(takeSemaphore.getQueueLength() + 4096);
        do {
            try {
                doReduceCreated(Integer.MAX_VALUE, true);
            }
            catch (RuntimeException ignored) { }
            catch (Error ignored) { }
        } while (!available.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTerminated() {
        return terminated.get();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isFair() {
        return takeSemaphore.isFair();
    }

    /** {@inheritDoc} */
    @Override
    public long takenCount() {
        return takenCount.get();
    }
}
