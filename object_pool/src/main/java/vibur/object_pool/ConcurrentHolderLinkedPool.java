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
 * the currently restored object has been taken before that from the object pool, and whether
 * this object is currently in taken state.
 *
 * <p>The object returned by the {@code take} methods is enclosed in a thin wrapper class which
 * is created by the object pool and which is implementing the {@link Holder} interface. The
 * underlying object is accessible via the interface's {@code getTarget()} method.
 *
 * <p>This object pool provides support for fairness with regards to the waiting taker's threads in
 * the same way as it is provided by the underlying {@link ConcurrentLinkedPool}.
 *
 * <p>This object pool has also support for an automated shrinking (reduction) of the number of
 * allocated on the object pool objects. This functionality is provided by the underlying
 * {@link ConcurrentLinkedPool}.
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

    private static class TargetHolder<T> implements Holder<T> {
        private final int targetId;
        private final T target;
        private final StackTraceElement[] stackTrace;
        private final long timestamp;

        private TargetHolder(int targetId, T target,
                             StackTraceElement[] stackTrace, long timestamp) {
            this.targetId = targetId;
            this.target = target;
            this.stackTrace = stackTrace;
            this.timestamp = timestamp;
        }

        @Override
        public final T getTarget() {
            return target;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return stackTrace;
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TargetHolder that = (TargetHolder) o;
            return targetId == that.targetId;
        }

        @Override
        public int hashCode() {
            return targetId;
        }
    }

    /**
     * Creates a new {@code ConcurrentHolderLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     * the default auto-shrinking parameters which are {@code 30} seconds
     * {@code timeout} and {@link DefaultPoolReducer}, and <i>no</i> additional
     * {@code Holder} info.
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
     * the default auto-shrinking parameters which are {@code 30} seconds
     * {@code timeout} and {@link DefaultPoolReducer}, and additional {@code Holder} info.
     *
     * @param poolObjectFactory the factory which will be used to create new objects
     *                          in this object pool as well as to control their lifecycle
     * @param initialSize       the object pool initial size, i.e. the initial number of
     *                          allocated in the object pool objects
     * @param maxSize           the object pool max size, i.e. the max number of allocated
     *                          in the object pool objects
     * @param fair              the object pool's fairness setting with regards to waiting threads
     * @param additionalInfo    determines whether the returned holder to include information for
     *                          the current stack trace and timestamp
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

    /**
     * Creates a new {@code ConcurrentHolderLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     * auto-shrinking parameters, and <i>no</i> additional {@code Holder} info.
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
    public ConcurrentHolderLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                      int initialSize, int maxSize, boolean fair,
                                      long timeout, TimeUnit unit, PoolReducer poolReducer) {
        this(poolObjectFactory, initialSize, maxSize, fair, timeout, unit, poolReducer, false);
    }

    /**
     * Creates a new {@code ConcurrentHolderLinkedPool} with the given
     * {@link PoolObjectFactory}, initial and max sizes, fairness setting,
     * auto-shrinking parameters, and additional {@code Holder} info.
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
     * @param additionalInfo    determines whether the returned holder to include information for
     *                          the current stack trace and timestamp
     * @throws IllegalArgumentException if the following holds:<br>
     *         {@code initialSize < 0 || maxSize < 1 || maxSize < initialSize}<br>
     * @throws NullPointerException if {@code poolObjectFactory} is null or if
     * (timeout > 0 && (unit == null || poolReducer == null))
     */
    public ConcurrentHolderLinkedPool(PoolObjectFactory<T> poolObjectFactory,
                                      int initialSize, int maxSize, boolean fair,
                                      long timeout, TimeUnit unit, PoolReducer poolReducer,
                                      boolean additionalInfo) {
        super(new ConcurrentLinkedPool<T>(
                poolObjectFactory, initialSize, maxSize, fair, timeout, unit, poolReducer));
        taken = new ConcurrentHashMap<Holder<T>, Boolean>(maxSize);
        this.additionalInfo = additionalInfo;
    }

    /** {@inheritDoc} */
    @Override
    public Holder<T> take() {
        T target = nonValidatingPoolService.take();
        if (target == null)
            return null;

        return newHolder(target);
    }

    /** {@inheritDoc} */
    @Override
    public Holder<T> takeUninterruptibly() {
        T target = nonValidatingPoolService.takeUninterruptibly();
        return newHolder(target);
    }

    /** {@inheritDoc} */
    @Override
    public Holder<T> tryTake(long timeout, TimeUnit unit) {
        T target = nonValidatingPoolService.tryTake(timeout, unit);
        if (target == null)
            return null;

        return newHolder(target);
    }

    /** {@inheritDoc} */
    @Override
    public Holder<T> tryTake() {
        T target = nonValidatingPoolService.tryTake();
        if (target == null)
            return null;

        return newHolder(target);
    }

    private Holder<T> newHolder(T target) {
        StackTraceElement[] stackTrace;
        long timestamp;
        if (additionalInfo) {
            stackTrace = new Throwable().getStackTrace();
            timestamp = System.currentTimeMillis();
        } else {
            stackTrace = null;
            timestamp = -1;
        }
        Holder<T> holder = new TargetHolder<T>(
                idGen.getAndIncrement(), target, stackTrace, timestamp);
        taken.put(holder, Boolean.TRUE);
        return holder;
    }

    /** {@inheritDoc} */
    @Override
    public boolean restore(Holder<T> holder) {
        T target = holder.getTarget();
        if (taken.remove(holder) == null)
            return false;

        nonValidatingPoolService.restore(target);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public List<Holder<T>> takenHolders() {
        return new ArrayList<Holder<T>>(taken.keySet());
    }
}
