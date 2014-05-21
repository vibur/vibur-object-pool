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

/**
 * Defines an interface which is to be implemented by the factory that will be used by the object
 * pools defined in this package, in order to control the lifecycle of the objects in the pools.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in the concrete object pool
 */
public interface PoolObjectFactory<T> {

    /**
     * Creates a new object for the calling object pool. This object is presumed to be ready (and valid)
     * for immediate use. Should <strong>never</strong> return {@code null}.
     *
     * <p>This method will be called by the constructors of {@link ConcurrentLinkedPool},
     * and by any of the {@code take...} methods of the before
     * mentioned two classes, if the {@code take...} methods were able to obtain a permit from the
     * counting {@code Semaphore} guarding the pool, but there was not an available and valid object
     * in the pool. I.e. this is the case when a new object is created (lazily) in the pool on request.
     *
     * @return a new object for this object pool
     */
    T create();

    /**
     * A validation/activation hook which will be called by the {@code take...} methods of
     * {@link ConcurrentLinkedPool} when an object from
     * the object pool is requested by the application. This is an optional operation
     * which concrete implementation may simply always return {@code true}.
     *
     * <p>If there is a particular activation or validation which needs to be done
     * for the taken from the pool object, this is the ideal place where it can be done.
     *
     * @see #readyToRestore
     *
     * @param obj an object which is taken from the object pool and which is to be given
     *            to the calling application
     * @return {@code true} if the validation/activation is successful, {@code false} otherwise
     */
    boolean readyToTake(T obj);

    /**
     * A validation/passivation hook which will be called by the {@code restore} methods of
     * {@link ConcurrentLinkedPool} when an object taken
     * before that from the object pool is about to be restored (returned back) to the pool.
     * This is an optional operation which concrete implementation may simply always return
     * {@code true}.
     *
     * <p>If there is a particular passivation or validation which needs to be done
     * for the restored to the pool object, this is the ideal place where it can be done.
     *
     * @see #readyToTake
     *
     * @param obj an object which has been taken before that from this object pool and which is now
     *            to be restored to the pool
     * @return {@code true} if the validation/passivation is successful, {@code false} otherwise
     */
    boolean readyToRestore(T obj);

    /**
     * A method which will be called when an object from the object pool needs to be destroyed,
     * which is when the {@link #readyToTake} or {@link #readyToRestore} methods have returned
     * {@code false}, or when the pool is shrinking its size (via calling {@code reduceCreated}),
     * or when the pool is terminating. The simplest implementation of this method may simply
     * do nothing, however if there are any allocated resources associated with the to-be-destroyed
     * object, like network connections or similar, this is the ideal place where they can be
     * de-allocated.
     *
     * @param obj an object from the pool which needs to be destroyed
     */
    void destroy(T obj);
}
