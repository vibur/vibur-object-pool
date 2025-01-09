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

import org.vibur.objectpool.util.ConcurrentCollection;
import org.vibur.objectpool.util.Listener;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static org.vibur.objectpool.util.ArgumentValidation.forbidIllegalArgument;

/**
 * An object pool based on a {@link ConcurrentCollection} guarded by a {@link Semaphore}. If the injected
 * in the pool {@code ConcurrentCollection} has native implementation for {@code offerFirst()} then
 * this pool will operate in LIFO mode, otherwise in FIFO mode.
 *
 * <p>This pool enforces a maximum limit on the number of objects that can be contained or taken out of it at
 * any time. The pool will lazily create an object upon {@link #tryTake take} request if no ready and valid object
 * exists in it at the time of the call; not all objects need to exist and be valid in the pool at all times.
 * The {@link #restore} methods do not provide any validation whether the currently restored object has been taken
 * before that from the pool or whether it is in taken state. Correct usage of the {@code restore} operations is
 * established by programming convention in the application.
 *
 * <p>The pool provides support for fairness with regard to the waiting takers threads.
 * The creation of new objects and their lifecycle are controlled by the supplied during the
 * pool creation time {@link PoolObjectFactory}. If a {@code Listener} instance has been
 * supplied when instantiating the pool, its methods will be called when the pool executes {@code take}
 * or {@code restore} operations.
 *
 * <p>This pool also provides support for shrinking (reduction) of the number of allocated in it objects.
 * Note that the shrinking may reduce the {@link #createdTotal()} to less than the pool {@link #initialSize()}.
 *
 * <p>The pool <b>cannot</b> contain {@code null} objects.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in the pool
 */
public class ConcurrentPool<T> implements PoolService<T> {

    private static final int RESERVED = 4096;
    private static final int MAX_ALLOWED_SIZE = Integer.MAX_VALUE - RESERVED;

    private final ConcurrentCollection<T> available;
    private final Semaphore takeSemaphore;

    private final PoolObjectFactory<T> poolObjectFactory;
    private final Listener<T> listener;

    private final int initialSize;
    private final int maxSize;
    private final AtomicInteger createdTotal;

    private final AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * Creates a new {@code ConcurrentPool} with the given {@link PoolObjectFactory}, initial and max sizes,
     * and fairness setting.
     *
     * @param available         the concurrent collection that will store the pooled objects;
     *                          it must be an empty collection or a collection pre-initialized with
     *                          {@code initialSize} objects
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects; this parameter never changes
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects; this parameter never changes
     * @param fair              the object pool fairness setting with regard to waiting threads
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}
     * @throws NullPointerException if {@code available} or {@code poolObjectFactory} are null
     */
    public ConcurrentPool(ConcurrentCollection<T> available, PoolObjectFactory<T> poolObjectFactory,
                          int initialSize, int maxSize, boolean fair) {
        this(available, poolObjectFactory, initialSize, maxSize, fair, null);
    }

    /**
     * Creates a new {@code ConcurrentPool} with the given {@link PoolObjectFactory}, initial and max sizes,
     * and fairness setting.
     *
     * @param available         the concurrent collection that will store the pooled objects;
     *                          it must be an empty collection or a collection pre-initialized with
     *                          {@code initialSize} objects
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects; this parameter never changes
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects; this parameter never changes
     * @param fair              the object pool fairness setting with regard to waiting threads
     * @param listener          if not {@code null}, this listener instance methods will be called
     *                          when the pool executes {@code take} or {@code restore} operations
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}
     * @throws NullPointerException if {@code available} or {@code poolObjectFactory} are null
     */
    public ConcurrentPool(ConcurrentCollection<T> available, PoolObjectFactory<T> poolObjectFactory,
                          int initialSize, int maxSize, boolean fair, Listener<T> listener) {
        forbidIllegalArgument(initialSize < 0, "initialSize");
        forbidIllegalArgument(maxSize < 1 || maxSize < initialSize || maxSize > MAX_ALLOWED_SIZE, "maxSize");
        var availableSize = available.size(); // implicit null check of available collection
        forbidIllegalArgument(availableSize != 0 && availableSize != initialSize, "available");

        this.available = available;
        this.poolObjectFactory = requireNonNull(poolObjectFactory);
        this.listener = listener;

        this.initialSize = initialSize;
        this.maxSize = maxSize;
        this.takeSemaphore = new Semaphore(maxSize, fair);

        this.createdTotal = new AtomicInteger(availableSize);
        if (availableSize == 0) {
            addInitialObjects();
        }
    }

    private void addInitialObjects() {
        try {
            for (var i = 0; i < initialSize; i++) {
                available.offerLast(requireNonNull(poolObjectFactory.create()));
                createdTotal.incrementAndGet();
            }
        } catch (Throwable t) { // equivalent to catching "RuntimeException | Error", however, better for Kotlin interoperability
            drainCreated();
            throw t;
        }
    }

    @Override
    public T take() {
        try {
            takeSemaphore.acquire();
            return takeObject();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    @Override
    public T take(long[] waitedNanos) {
        try {
            var startTime = System.nanoTime();
            try {
                takeSemaphore.acquire();
            } finally {
                waitedNanos[0] = System.nanoTime() - startTime;
            }

            return takeObject();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    @Override
    public T takeUninterruptibly() {
        takeSemaphore.acquireUninterruptibly();
        return takeObject();
    }

    @Override
    public T takeUninterruptibly(long[] waitedNanos) {
        var startTime = System.nanoTime();
        try {
            takeSemaphore.acquireUninterruptibly();
        } finally {
            waitedNanos[0] = System.nanoTime() - startTime;
        }

        return takeObject();
    }

    @Override
    public T tryTake(long timeout, TimeUnit unit) {
        try {
            if (!takeSemaphore.tryAcquire(timeout, unit)) {
                return null;
            }
            return takeObject();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    @Override
    public T tryTake(long timeout, TimeUnit unit, long[] waitedNanos) {
        try {
            var startTime = System.nanoTime();
            try {
                if (!takeSemaphore.tryAcquire(timeout, unit)) {
                    return null;
                }
            } finally {
                waitedNanos[0] = System.nanoTime() - startTime;
            }

            return takeObject();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); // ignore and reset
            return null;
        }
    }

    @Override
    public T tryTake() {
        if (!takeSemaphore.tryAcquire()) {
            return null;
        }
        return takeObject();
    }

    private T takeObject() {
        var object = prepareToTake(available.pollFirst());

        if (listener != null) {
            listener.onTake(object);
        }

        if (isTerminated()) {
            restore(object, false);
            return null;
        }

        return object;
    }

    @Override
    public void restore(T object) {
        restore(object, true);
    }

    @Override
    public void restore(T object, boolean valid) {
        var ready = readyToRestore(requireNonNull(object), valid);

        if (listener != null) {
            listener.onRestore(object, valid);
        }

        if (ready) {
            available.offerFirst(object);
        }
        takeSemaphore.release();

        if (isTerminated() && valid) {
            terminate();
        }
    }

    /**
     * Verifies whether the given object is valid and whether it can be given to the calling application.
     * If the object is {@code null}, a new object will be created and returned. If the object
     * is not {@code null}, it will be validated and if the validation fails, a new object will be created
     * and returned; otherwise a reference to the object itself will be returned. This method <b>never</b>
     * returns {@code null}.
     *
     * @param object the object to be validated
     * @return see above
     */
    private T prepareToTake(T object) {
        try {
            if (object == null) {
                createdTotal.incrementAndGet();
                object = requireNonNull(poolObjectFactory.create());
            }
            else {
                var ready = false;
                try {
                    ready = poolObjectFactory.readyToTake(object);
                } finally {
                    if (!ready) {
                        poolObjectFactory.destroy(object);
                    }
                }
                if (!ready) {
                    object = requireNonNull(poolObjectFactory.create());
                }
            }

            return object;
        } catch (Throwable t) { // equivalent to catching "RuntimeException | Error", however, better for Kotlin interoperability
            recoverInnerState();
            throw t;
        }
    }

    /**
     * Verifies whether the given object is valid and whether it can be restored back to the pool. Returns {@code true}
     * if the object is valid; otherwise returns {@code false}.
     *
     * @param object the object that is to be restored to the pool and that needs to be validated
     * @param valid if {@code false}, the object is treated as invalid; otherwise a secondary validation on the object
     *              will be performed
     * @return see above
     */
    private boolean readyToRestore(T object, boolean valid) {
        try {
            var ready = false;
            try {
                ready = valid && poolObjectFactory.readyToRestore(object);
            } finally {
                if (!ready) {
                    poolObjectFactory.destroy(object);
                }
            }
            if (!ready) {
                createdTotal.decrementAndGet();
            }

            return ready;
        } catch (Throwable t) { // equivalent to catching "RuntimeException | Error", however, better for Kotlin interoperability
            recoverInnerState();
            throw t;
        }
    }

    private void recoverInnerState() {
        createdTotal.decrementAndGet();
        takeSemaphore.release();
    }


    @Override
    public Listener<T> listener() {
        return listener;
    }

    @Override
    public int taken() {
        return !isTerminated() ? calculateTaken() : createdTotal();
    }

    private int calculateTaken() {
        return maxSize() - remainingCapacity();
    }

    @Override
    public int remainingCreated() {
        return !isTerminated() ? createdTotal() - calculateTaken() : 0;
    }

    @Override
    public int drainCreated() {
        return reduceCreatedTo(0, true);
    }

    @Override
    public String toString() {
        return super.toString() + (!isTerminated() ? "[remainingCreated = " + remainingCreated() + "]" : "[terminated]");
    }


    @Override
    public int createdTotal() {
        return createdTotal.get();
    }

    @Override
    public int remainingCapacity() {
        return !isTerminated() ? takeSemaphore.availablePermits() : 0;
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
    public int reduceCreatedBy(int reduceBy, boolean ignoreInitialSize) {
        forbidIllegalArgument(reduceBy < 0, "reduceBy");

        for (var cnt = 0; cnt < reduceBy; cnt++) {
            if (!reduceByOne(ignoreInitialSize)) {
                return cnt;
            }
        }
        return reduceBy;
    }

    @Override
    public int reduceCreatedTo(int reduceTo, boolean ignoreInitialSize) {
        forbidIllegalArgument(reduceTo < 0, "reduceTo");

        int cnt;
        for (cnt = 0; createdTotal() > reduceTo; cnt++) {
            if (!reduceByOne(ignoreInitialSize)) {
                break;
            }
        }
        return cnt;
    }

    private boolean reduceByOne(boolean ignoreInitialSize) {
        var newTotal = createdTotal.decrementAndGet();
        if (!ignoreInitialSize && newTotal < initialSize) {
            createdTotal.incrementAndGet();
            return false;
        }
        var object = available.pollLast();
        if (object == null) {
            createdTotal.incrementAndGet();
            return false;
        }
        poolObjectFactory.destroy(object);
        return true;
    }


    @Override
    public void terminate() {
        var wasTerminated = terminated.getAndSet(true);

        drainCreated();

        if (!wasTerminated) {
            takeSemaphore.release(takeSemaphore.getQueueLength() + RESERVED); // best effort to unblock any waiting on the takeSemaphore threads
        }
    }

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
