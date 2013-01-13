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

import java.util.concurrent.TimeUnit;

/**
 * Extends the functionality defined by {@link BasePoolService} with simple
 * take and restore methods which don't provide any means for validation of whether the
 * restored (returned) object is one which has been taken before that from this object pool,
 * neither whether the object is restored more than once to the object pool. The correctness of
 * the restore operation remains responsibility of the calling application.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public interface NonValidatingPoolService<T> extends BasePoolService {

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call which
     * waits indefinetly until an object becomes available. If the calling thread is interrupted
     * while waiting this call will return {@code null} and the thread's interrupted status will
     * be set to {@code true}.
     *
     * @return an object taken from the object pool or {@code null} if was interrupted while waiting
     */
    T take();

    /**
     * Takes an object from the object pool if there is such available. This is a blocking call which
     * waits indefinetly until an object becomes available.
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
     * Restores (returns) an object to the object pool. The object pool will <strong>not</strong> do any validation
     * whether the object restored has been taken before from this object pool or whether it is restored more
     * than once.
     *
     * @param object an object to be restored (returned) to this object pool
     */
    void restore(T object);
}
