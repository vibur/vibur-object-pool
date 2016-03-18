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

import static java.util.Objects.requireNonNull;
import static org.vibur.objectpool.util.ArgumentUtils.forbidIllegalArgument;

/**
 * An object pool based on a {@link ConcurrentLinkedQueue} guarded by a {@link Semaphore}. This
 * object pool does not provide any validation whether the currently restored object
 * has been taken before that from the object pool or whether this object is currently in taken state.
 * Correct usage of the pool is established by programming convention in the application.
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
    private final int maxSize;
    private final AtomicInteger createdTotal;

    private final AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * Creates a new {@code ConcurrentLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects; this parameter never changes
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects; this parameter never changes
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
     *                          allocated in the object pool objects; this parameter never changes
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects; this parameter never changes
     * @param fair              the object pool fairness setting with regards to waiting threads
     * @param listener          if not {@code null}, this listener instance methods will be called
     *                          when the pool executes {@code take} or {@code restore} operations
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}
     * @throws NullPointerException if {@code poolObjectFactory} is null
     */
    public ConcurrentLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                int initialSize, int maxSize, boolean fair, Listener<T> listener) {
        forbidIllegalArgument(initialSize < 0);
        forbidIllegalArgument(maxSize < 1);
        forbidIllegalArgument(maxSize < initialSize);

        this.poolObjectFactory = requireNonNull(poolObjectFactory);
        this.listener = listener;

        this.initialSize = initialSize;
        this.maxSize = maxSize;
        this.takeSemaphore = new Semaphore(maxSize, fair);

        this.available = new ConcurrentLinkedQueue<>();
        this.createdTotal = new AtomicInteger(0);
        for (int i = 0; i < initialSize; i++) {
            available.add(create());
            createdTotal.incrementAndGet();
        }
    }

    private T create() {
        return requireNonNull(poolObjectFactory.create());
    }

    @Override
    public T take() {
        try {
            takeSemaphore.acquire();
            return newObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    @Override
    public T takeUninterruptibly() {
        takeSemaphore.acquireUninterruptibly();
        return newObject();
    }

    @Override
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

    @Override
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

    @Override
    public void restore(T object) {
        restore(object, true);
    }

    @Override
    public void restore(T object, boolean valid) {
        requireNonNull(object);
        if (isTerminated()) {
            createdTotal.decrementAndGet();
            poolObjectFactory.destroy(object);
            return;
        }

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
            }
            else {
                boolean ready = false;
                try {
                    ready = poolObjectFactory.readyToTake(object);
                } finally {
                    if (!ready)
                        poolObjectFactory.destroy(object);
                }
                if (!ready)
                    object = create();
            }
            return object;
        } catch (RuntimeException | Error e) {
            recoverInnerState(1);
            throw e;
        }
    }

    private T prepareToRestore(T object, boolean valid) {
        try {
            boolean ready = false;
            try {
                ready = valid && poolObjectFactory.readyToRestore(object);
            } finally {
                if (!ready)
                    poolObjectFactory.destroy(object);
            }
            if (!ready) {
                createdTotal.decrementAndGet();
                object = null;
            }
            return object;
        } catch (RuntimeException | Error e) {
            recoverInnerState(1);
            throw e;
        }
    }

    private void recoverInnerState(int permits) {
        createdTotal.addAndGet(-permits);
        takeSemaphore.release(permits);
    }


    @Override
    public Listener<T> listener() {
        return listener;
    }

    @Override
    public int taken() {
        return isTerminated() ? createdTotal() : calculateTaken();
    }

    private int calculateTaken() {
        return maxSize() - remainingCapacity();
    }

    @Override
    public int remainingCreated() { // faster implementation than calling {@code available.size())
        return isTerminated() ? 0 : createdTotal() - calculateTaken();
    }

    @Override
    public int drainCreated() {
        return reduceCreated(Integer.MAX_VALUE, true);
    }

    @Override
    public String toString() {
        return super.toString() + (isTerminated() ? "[terminated]"
            : "[remainingCreated = " + remainingCreated() + "]");
    }


    @Override
    public int createdTotal() {
        return createdTotal.get();
    }

    @Override
    public int remainingCapacity() {
        return isTerminated() ? 0 : takeSemaphore.availablePermits();
    }

    @Override
    public int initialSize() {
        return initialSize;
    }

    @Override
    public int maxSize() {
        return maxSize;
    }


    @Override
    public int reduceCreated(int reduction, boolean ignoreInitialSize) {
        if (reduction < 0)
            throw new IllegalArgumentException();

        for (int cnt = 0; cnt < reduction; cnt++) {
            int newTotal = createdTotal.decrementAndGet();
            if (!ignoreInitialSize && newTotal < initialSize) {
                createdTotal.incrementAndGet();
                return cnt;
            }
            T object = available.poll();
            if (object == null) {
                createdTotal.incrementAndGet();
                return cnt;
            }
            poolObjectFactory.destroy(object);
        }
        return reduction;
    }


    @Override
    public void terminate() {
        if (terminated.getAndSet(true))
            return;

        // best effort to unblock any waiting on the takeSemaphore threads
        takeSemaphore.release(takeSemaphore.getQueueLength() + 4096);

        drainCreated();
    }

    /**
     * A synonym for {@link #terminate()}.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void close() {
        terminate();
    }

    @Override
    public boolean isTerminated() {
        return terminated.get();
    }


    @Override
    public boolean isFair() {
        return takeSemaphore.isFair();
    }
}
