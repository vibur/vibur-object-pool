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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of a <i>validating</i> object pool which is build on (composed) using
 * a non-validating {@link ConcurrentLinkedPool} and is utilising a {@link ConcurrentHashMap}
 * for the validation of the restored objects. The validation checks whether
 * the currently restored object holder has been taken before that from the object pool,
 * and whether it is currently in taken state.
 *
 * <p>The object returned by the {@code take} methods is enclosed in a thin wrapper class which
 * is created by the object pool and which is implementing the {@link Holder} interface. The
 * underlying object is accessible via the interface's {@code value()} method.
 *
 * <p>This object pool provides support for fairness with regards to the waiting taker's threads in
 * the same way as it is provided by the underlying {@link ConcurrentLinkedPool}.
 *
 * <p>This object pool has support for shrinking (reduction) of the number of
 * allocated on the pool objects. This functionality is provided by the underlying
 * {@link ConcurrentLinkedPool}. Note that the shrinking may reduce the
 * {@link #createdTotal()} to less than the  pool {@link #initialSize()}.
 *
 * @see ConcurrentLinkedPool
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public class ConcurrentHolderLinkedPool<T> extends AbstractValidatingPoolService<T>
        implements HolderValidatingPoolService<T> {

    private final ConcurrentMap<Holder<T>, Boolean> taken;
    private final AtomicInteger idGen = new AtomicInteger(0);
    private final boolean additionalInfo;

    private static class ObjectHolder<T> implements Holder<T> {
        private final int valueId;
        private final T value;
        private final StackTraceElement[] stackTrace;

        private ObjectHolder(int valueId, T value, StackTraceElement[]  stackTrace) {
            this.valueId = valueId;
            this.value = value;
            this.stackTrace = stackTrace;
        }

        public T value() { return value; }
        public StackTraceElement[] getStackTrace() { return stackTrace; }

        public int hashCode() { return valueId; }

        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            return valueId == ((ObjectHolder) obj).valueId;
        }
    }

    /**
     * Creates a new {@code ConcurrentHolderLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     * and no additional {@code Holder} info.
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
    public ConcurrentHolderLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                      int initialSize, int maxSize,
                                      boolean fair) {
        this(poolObjectFactory, initialSize, maxSize, fair, false);
    }

    /**
     * Creates a new {@code ConcurrentHolderLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     * and additional {@code Holder} info.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects
     * @param fair              the object pool's fairness setting with regards to waiting threads
     * @param additionalInfo    determines whether the returned object holder will include
     *                          information for the current stack trace
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}<br>
     * @throws NullPointerException if {@code poolObjectFactory} is null
     */
    public ConcurrentHolderLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                      int initialSize, int maxSize,
                                      boolean fair, boolean additionalInfo) {
        super(new ConcurrentLinkedPool<T>(
                poolObjectFactory, initialSize, maxSize, fair));
        taken = new ConcurrentHashMap<Holder<T>, Boolean>(maxSize);
        this.additionalInfo = additionalInfo;
    }

    /** {@inheritDoc} */
    public Holder<T> take() {
        T object = nonValidatingPoolService.take();
        if (object == null)
            return null;

        return newHolder(object);
    }

    /** {@inheritDoc} */
    public Holder<T> takeUninterruptibly() {
        T object = nonValidatingPoolService.takeUninterruptibly();
        return newHolder(object);
    }

    /** {@inheritDoc} */
    public Holder<T> tryTake(long timeout, TimeUnit unit) {
        T object = nonValidatingPoolService.tryTake(timeout, unit);
        if (object == null)
            return null;

        return newHolder(object);
    }

    /** {@inheritDoc} */
    public Holder<T> tryTake() {
        T object = nonValidatingPoolService.tryTake();
        if (object == null)
            return null;

        return newHolder(object);
    }

    private Holder<T> newHolder(T object) {
        StackTraceElement[] stackTrace = additionalInfo ? new Throwable().getStackTrace() : null;
        Holder<T> holder = new ObjectHolder<T>(idGen.getAndIncrement(), object, stackTrace);
        taken.put(holder, Boolean.TRUE);
        return holder;
    }

    /** {@inheritDoc} */
    public boolean restore(Holder<T> holder) {
        return restore(holder, true);
    }

    /** {@inheritDoc} */
    public boolean restore(Holder<T> holder, boolean valid) {
        if (taken.remove(holder) == null)
            return false;

        nonValidatingPoolService.restore(holder.value(), valid);
        return true;
    }

    /** {@inheritDoc} */
    public List<Holder<T>> takenHolders() {
        return new ArrayList<Holder<T>>(taken.keySet());
    }
}
