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

import org.vibur.objectpool.util.Listener;

import java.util.concurrent.TimeUnit;

/**
 * Defines the object pool operations. These operations include the {@code take} and {@code restore} pool
 * methods.
 *
 * <p>This pool enforces a maximum limit on the number of objects that can be contained or taken out
 * of it at any time. The pool may lazily create an object upon {@code take} request; not all objects need
 * to exist and be valid in the pool at all times. The {@code restore} methods do not provide any validation
 * whether the currently restored object has been taken before that from the pool or whether it is in taken state.
 * Correct usage of the {@code restore} operations is established by programming convention in the application.
 *
 * <p>The object pool implementation may support an optional fairness parameter (usually provided via the
 * pool constructor) that defines the pool behaviour with regards to waiting takers threads, as well as
 * an optional {@code Listener} interface which methods will be called when a {@code take} or
 * {@code restore} pool method executes.
 *
 * <p>The pool <b>cannot</b> contain {@code null} objects.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public interface PoolService<T> extends BasePool {

    /**
     * A counterpart of {@link #take(long[])} that does <i>not</i> report back the time waited
     * to obtain an object from the pool.
     *
     * @return an object taken from the object pool or {@code null} if was interrupted while waiting
     */
    T take();

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call that
     * waits indefinitely until an object becomes available; the object may need to be created as
     * described in {@link #tryTake(long, TimeUnit, long[])}. If the calling thread is interrupted
     * while waiting this call will return {@code null} and the thread's interrupted status will
     * be set to {@code true}.
     *
     * @param waitedNanos used to report the time waited, see {@link #tryTake(long, TimeUnit, long[])}
     * @return an object taken from the object pool or {@code null} if it was interrupted while waiting
     */
    T take(long[] waitedNanos);

    /**
     * A counterpart of {@link #takeUninterruptibly(long[])} that does <i>not</i> report back the time waited
     * to obtain an object from the pool.
     *
     * @return an object taken from the object pool
     */
    T takeUninterruptibly();

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call that
     * waits indefinitely until an object becomes available; the object may need to be created as
     * described in {@link #tryTake(long, TimeUnit, long[])}.
     *
     * @param waitedNanos used to report the time waited, see {@link #tryTake(long, TimeUnit, long[])}
     * @return an object taken from the object pool
     */
    T takeUninterruptibly(long[] waitedNanos);

    /**
     * A counterpart of {@link #tryTake(long, TimeUnit, long[])} that does <i>not</i> report back the time waited
     * to obtain an object from the pool.
     *
     * @param timeout the maximum time to wait for an object to become available in the object pool;
     *                this timeout does not include the object creation time, see above
     * @param unit the time unit of the {@code timeout} argument
     * @return an object taken from the object pool or {@code null} if the specified timeout expires
     * or if it was interrupted while waiting
     */
    T tryTake(long timeout, TimeUnit unit);

    /**
     * Tries to take an object from the object pool if there is one available. This is a blocking call that
     * waits for an object to become available up to the specified {@code timeout}. The real time spent waiting is
     * reported back via the {@code waitedNanos} parameter. The total method execution time may also include the
     * object creation time - an object can be (lazily) created in the pool when the pool capacity is not reached
     * yet but no ready and valid object existed in the pool. If the calling thread is interrupted while waiting
     * this call will return {@code null} and the thread's interrupted status will be set to {@code true}.
     *
     * @param timeout the maximum time to wait for an object to become available in the object pool;
     *                this timeout does not include the object creation time
     * @param unit the time unit of the {@code timeout} argument
     * @param waitedNanos this parameter is used to report the nanoseconds time waited for an object to become
     *                    available in the pool, excluding any object creation time; the time waited will be stored
     *                    at index {@code 0} of this array; the array must be of size of at least one
     * @return an object taken from the object pool or {@code null} if the specified timeout expires
     * or if it was interrupted while waiting
     */
    T tryTake(long timeout, TimeUnit unit, long[] waitedNanos);

    /**
     * Tries to take an object from the object pool if there is one that is immediately available; the object may
     * need to be created as described in {@link #tryTake(long, TimeUnit, long[])}. Returns {@code null} if no object
     * is available in the pool at the time of the call.
     *
     * @return an object from the object pool or {@code null} if no object was available
     */
    T tryTake();

    /**
     * Restores (returns) an object to the object pool. The object pool does <b>not</b>
     * validate whether the currently restored object has been taken before that from this object pool
     * or whether it is currently in taken state. Equivalent to calling {@code restore(object, true)}.
     *
     * @param object an object to be restored (returned) to this object pool
     */
    void restore(T object);

    /**
     * Restores (returns) an object to the object pool. The object pool does <b>not</b>
     * validate whether the currently restored object has been taken before that from this object pool
     * or whether it is currently in taken state.
     *
     * @param object an object to be restored (returned) to this object pool
     * @param valid  if {@code true} the restored object is presumed to be in valid (healthy) state,
     *               otherwise it is treated as invalid
     */
    void restore(T object, boolean valid);


    /**
     * Returns the {@link Listener} interface instance associated with this object pool, if any.
     *
     * @return  see above; {@code null} means no {@code Listener} is associated with this object pool.
     */
    Listener<T> listener();


    /**
     * Returns the fairness setting of this object pool.
     *
     * @return {@code true} if the object pool is fair to waiting taker threads
     */
    boolean isFair();
}
