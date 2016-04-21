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
 * Defines the object pool operations. These operations include the {@code take} and
 * {@code restore} methods. The {@code restore} method do not provide any validation whether
 * the currently restored object has been taken before from the pool or whether the object is
 * currently in taken state. Correct usage of these operations is established by programming
 * convention in the application.
 *
 * <p>The object pool implementation may support an optional fairness parameter (usually provided via the
 * pool constructor) that defines the pool behaviour with regards to waiting takers threads, as well as
 * an optional {@code Listener} interface which methods will be called when a {@code take} or
 * {@code restore} pool method executes.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public interface PoolService<T> extends BasePool {

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
     * Returns the {@link Listener} interface instance associated with this object pool, if any.
     *
     * @return  see above. {@code null} means no {@code Listener} is associated with this object pool.
     */
    Listener<T> listener();


    /**
     * Returns the fairness setting of this object pool.
     *
     * @return {@code true} if the object pool is fair to waiting taker threads
     */
    boolean isFair();
}
