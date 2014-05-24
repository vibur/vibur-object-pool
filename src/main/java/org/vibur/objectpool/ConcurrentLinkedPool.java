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

package org.vibur.objectpool;

import org.vibur.objectpool.listener.Listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An object pool based on a {@link ConcurrentLinkedQueue} guarded by a {@link Semaphore}. This
 * object pool does <b>not</b> implement any validation of whether the currently restored object
 * has been taken before that from the object pool, or whether this object is currently in taken state.
 *
 * <p>This object pool provides support for fairness with regards to the waiting takers threads.
 * The creation of new objects and their lifecycle are controlled by a supplied during the
 * object pool creation time {@link PoolObjectFactory}. If a {@code Listener} instance has been
 * supplied when instantiating the pool, its methods will be when the pool executes {@code take}
 * or {@code restore} operations.
 *
 * <p>This object pool has support for shrinking (reduction) of the number of
 * allocated on the pool objects. Note that the shrinking may reduce the
 * {@link #createdTotal()} to less than the  pool {@link #initialSize()}.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public class ConcurrentLinkedPool<T> implements PoolService<T> {

    private final PoolObjectFactory<T> poolObjectFactory;
    private final Listener<T> listener;

    private final Semaphore takeSemaphore;
    private final Queue<T> available;

    private final int initialSize;
    private final AtomicInteger maxSize;
    private final AtomicInteger createdTotal;

    private final AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * Creates a new {@code ConcurrentLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects
     * @param fair              the object pool fairness setting with regards to waiting threads
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}
     * @throws NullPointerException if {@code poolObjectFactory} is null
     */
    public ConcurrentLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                int initialSize, int maxSize, boolean fair) {
        this(poolObjectFactory, initialSize, maxSize, fair, null);
    }

    /**
     * Creates a new {@code ConcurrentLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects
     * @param fair              the object pool fairness setting with regards to waiting threads
     * @param listener          if not {@code null}, this listener instance methods will be called
     *                          when the pool executes {@code take} or {@code restore} operations
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}
     * @throws NullPointerException if {@code poolObjectFactory} is null
     */
    public ConcurrentLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                int initialSize, int maxSize, boolean fair, Listener<T> listener) {
        if (initialSize < 0 || maxSize < 1 || maxSize < initialSize)
            throw new IllegalArgumentException();
        if (poolObjectFactory == null)
            throw new NullPointerException();

        this.poolObjectFactory = poolObjectFactory;
        this.listener = listener;
        this.takeSemaphore = new Semaphore(maxSize, fair);

        this.available = new ConcurrentLinkedQueue<T>();
        for (int i = 0; i < initialSize; i++) {
            this.available.add(create());
        }

        this.initialSize = initialSize;
        this.maxSize = new AtomicInteger(maxSize);
        this.createdTotal = new AtomicInteger(initialSize);
    }

    private T create() {
        T object = poolObjectFactory.create();
        if (object == null) throw new NullPointerException();
        return object;
    }

    /** {@inheritDoc} */
    public T take() {
        try {
            takeSemaphore.acquire();
            return newObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    /** {@inheritDoc} */
    public T takeUninterruptibly() {
        takeSemaphore.acquireUninterruptibly();
        return newObject();
    }

    /** {@inheritDoc} */
    public T tryTake(long timeout, TimeUnit unit) {
        try {
            if (!takeSemaphore.tryAcquire(timeout, unit))
                return null;
            return newObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    /** {@inheritDoc} */
    public T tryTake() {
        if (!takeSemaphore.tryAcquire())
            return null;
        return newObject();
    }

    protected T newObject() {
        if (isTerminated()) {
            takeSemaphore.release();
            return null;
        }

        T object = available.poll();
        object = prepareToTake(object);
        if (listener != null)
            listener.onTake(object);
        return object;
    }

    /** {@inheritDoc} */
    public void restore(T object) {
        restore(object, true);
    }

    /** {@inheritDoc} */
    public void restore(T object, boolean valid) {
        if (object == null) throw new NullPointerException();
        if (isTerminated())
            return;

        if (listener != null)
            listener.onRestore(object);
        object = prepareToRestore(object, valid);
        if (object != null)
            available.add(object);
        takeSemaphore.release();
    }

    private T prepareToTake(T object) {
        try {
            if (object == null) {
                createdTotal.incrementAndGet();
                object = create();
            } else if (!poolObjectFactory.readyToTake(object)) {
                poolObjectFactory.destroy(object);
                object = create();
            }
            return object;
        } catch (RuntimeException e) {
            recoverInnerState(1); throw e;
        } catch (Error e) {
            recoverInnerState(1); throw e;
        }
    }

    private T prepareToRestore(T object, boolean valid) {
        try {
            if (!valid || !poolObjectFactory.readyToRestore(object)) {
                poolObjectFactory.destroy(object);
                createdTotal.decrementAndGet();
                object = null;
            }
            return object;
        } catch (RuntimeException e) {
            recoverInnerState(1); throw e;
        } catch (Error e) {
            recoverInnerState(1); throw e;
        }
    }

    private void recoverInnerState(int permits) {
        createdTotal.addAndGet(-permits);
        takeSemaphore.release(permits);
    }


    /** {@inheritDoc} */
    public Listener<T> listener() {
        return listener;
    }

    /** {@inheritDoc} */
    public int taken() {
        return isTerminated() ? maxSize() : calculateTaken();
    }

    private int calculateTaken() {
        return maxSize() - remainingCapacity();
    }

    /** {@inheritDoc} */
    public int remainingCreated() { // faster implementation than calling {@code available.size())
        return isTerminated() ? 0 : createdTotal() - calculateTaken();
    }

    /** {@inheritDoc} */
    public int drainCreated() {
        return reduceCreated(Integer.MAX_VALUE, true);
    }

    /** {@inheritDoc} */
    public String toString() {
        return super.toString() + (isTerminated() ? "[terminated]"
            : "[remainingCreated = " + remainingCreated() + "]");
    }


    /** {@inheritDoc} */
    public int createdTotal() {
        return createdTotal.get();
    }

    /** {@inheritDoc} */
    public int remainingCapacity() {
        return isTerminated() ? 0 : takeSemaphore.availablePermits();
    }

    /** {@inheritDoc} */
    public int initialSize() {
        return initialSize;
    }

    /** {@inheritDoc} */
    public int maxSize() {
        return maxSize.get();
    }


    /** {@inheritDoc} */
    public int reduceCreated(int reduction, boolean ignoreInitialSize) {
        if (reduction < 0)
            throw new IllegalArgumentException();

        int cnt;
        for (cnt = 0; cnt < reduction; cnt++) {
            int newTotal = createdTotal.decrementAndGet();
            if (!ignoreInitialSize && newTotal < initialSize) {
                createdTotal.incrementAndGet();
                break;
            }
            T object = available.poll();
            if (object == null) {
                createdTotal.incrementAndGet();
                break;
            }
            poolObjectFactory.destroy(object);
        }
        return cnt;
    }


    /** {@inheritDoc} */
    public void terminate() {
        if (terminated.getAndSet(true))
            return;

        // best effort to unblock any waiting on the takeSemaphore threads
        takeSemaphore.release(takeSemaphore.getQueueLength() + 4096);

        drainCreated();
    }

    /** {@inheritDoc} */
    public boolean isTerminated() {
        return terminated.get();
    }


    /** {@inheritDoc} */
    public boolean isFair() {
        return takeSemaphore.isFair();
    }
}
