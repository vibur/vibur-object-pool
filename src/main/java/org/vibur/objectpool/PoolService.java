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

import java.util.concurrent.TimeUnit;

/**
 * Defines the object pool operations. These operations include {@code take} and
 * {@code restore} methods which don't provide any means for validation of whether the
 * restored (returned) object is one which has been taken before that from the object pool,
 * neither whether this object is currently in taken state. The correctness of
 * the restore operation remains responsibility of the calling application.
 *
 * <p>The object pool has support for shrinking (reduction) of the number of
 * allocated on the pool objects.
 *
 * <p>The object pool may support an optional fairness parameter with regards to the waiting
 * takers threads, as well as an optional {@code Listener} interface which methods will be
 * called when a {@code take} or {@code restore} pool method executes.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public interface PoolService<T> {

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call which
     * waits indefinitely until an object becomes available. If the calling thread is interrupted
     * while waiting this call will return {@code null} and the thread's interrupted status will
     * be set to {@code true}.
     *
     * @return an object taken from the object pool or {@code null} if was interrupted while waiting
     */
    T take();

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call which
     * waits indefinitely until an object becomes available.
     *
     * @return an object taken from the object pool
     */
    T takeUninterruptibly();

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call which
     * waits up to the specified {@code timeout}  for an object to become available. If the calling
     * thread is interrupted while waiting this call will return {@code null} and the thread's
     * interrupted status will be set to {@code true}.
     *
     * @param timeout the maximum time to wait for an object to become available in the object pool
     * @param unit the time unit of the {@code timeout} argument
     * @return an object taken from the object pool or {@code null} if the specified timeout expires
     * or if it was interrupted while waiting
     */
    T tryTake(long timeout, TimeUnit unit);

    /**
     * Tries to take an object from the object pool if there is one which is immediately available. Returns
     * {@code null} if no object is available at the moment of the call.
     *
     * @return an object from the object pool or {@code null} if there is no object available in the object pool
     */
    T tryTake();

    /**
     * Restores (returns) an object to the object pool. The object pool will <strong>not</strong>
     * validate whether the currently restored object has been taken before that from this object pool
     * or whether it is currently in taken state. Equivalent to calling {@code restore(Object, true)}.
     *
     * @param object an object to be restored (returned) to this object pool
     */
    void restore(T object);

    /**
     * Restores (returns) an object to the object pool. The object pool will <strong>not</strong>
     * validate whether the currently restored object has been taken before that from this object pool
     * or whether it is currently in taken state.
     *
     * @param object an object to be restored (returned) to this object pool
     * @param valid  if {@code true} the restored object is presumed to be in valid (healthy) state,
     *               otherwise it is treated as invalid
     */
    void restore(T object, boolean valid);


    /**
     * Returns the {@link Listener} interface instance associated with this object pool.
     *
     * @return  see above. {@code null} means no {@code Listener} is associated with this object pool.
     */
    Listener<T> listener();

    /**
     * Returns the number of objects taken from this object pool.
     * This number is less than or equal to the object pool {@link #createdTotal()}.
     * Typically used for testing and debugging purposes.
     *
     * @return the number of objects taken from this object pool
     */
    int taken();

    /**
     * Returns the number of remaining created objects which currently exist in this object pool.
     * This number is less than or equal to the object pool {@link #remainingCapacity()}.
     * Typically used for testing and debugging purposes.
     *
     * @return the number of remaining created objects in this object pool
     */
    int remainingCreated();

    /**
     * Returns the total number of created objects which currently exist for this object pool.
     * This number is equal to {@link #taken()} + {@link #remainingCreated()}.
     * Typically used for testing and debugging purposes.
     *
     * @return the total number of created objects for this object pool
     */
    int createdTotal();

    /**
     * Returns the remaining capacity of this object pool, i.e. the number of objects which could be
     * taken from this object pool without blocking. It is not guaranteed that all these objects
     * exist at the time of the call (i.e. are already created) in the object pool - some of them
     * might be created on demand upon take requests. Also see {@link #remainingCreated()}.
     * Typically used for testing and debugging purposes.
     *
     * @return the object pool remaining capacity
     */
    int remainingCapacity();

    /**
     * Returns the {@code initialSize} of this object pool at construction time.
     * This parameter never changes.
     *
     * @return the object pool {@code initialSize}
     */
    int initialSize();

    /**
     * Returns the {@code maxSize} of this object pool. This parameter never changes.
     *
     * @return the object pool {@code maxSize}
     */
    int maxSize();


    /**
     * Tries to remove (and destroy) up to {@code reduction} objects from the object pool.
     * May bring the object pool {@link #createdTotal()} to a number less then its {@link #initialSize()}.
     *
     * @param reduction         the desired amount of objects to be removed
     * @param ignoreInitialSize specifies whether the {@link #createdTotal()} may be
     *                          reduced to less than {@link #initialSize()}
     * @return the actual amount of objects removed
     */
    int reduceCreated(int reduction, boolean ignoreInitialSize);

    /**
     * Tries to remove (and destroy) as many created objects from this object pool as possible.
     * May bring the object pool {@link #createdTotal()} to a number less then its {@link #initialSize()}.
     *
     * @return the actual amount of objects removed (and destroyed)
     */
    int drainCreated();


    /**
     * Terminates this object pool. Once terminated the object pool cannot be more revived.
     * All take and restore operations called on a terminated object pool should throw
     * an exception or be ignored. Invocation has no additional effect if already terminated.
     */
    void terminate();

    /**
     * Returns the current terminated state of this object pool.
     *
     * @return {@code true} if the object pool is terminated
     */
    boolean isTerminated();


    /**
     * Returns the fairness setting of this object pool.
     *
     * @return {@code true} if the object pool is fair to waiting taker threads
     */
    boolean isFair();
}
