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

/**
 * Defines an interface which is to be implemented by a factory which will be used by
 * the pools defined in this package to control the lifecycle of the object pool's objects.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this object pool
 */
public interface PoolObjectFactory<T> {

    /**
     * Creates a new object pool's object, which is presumed to be ready (and valid) for
     * immediate use.
     *
     * @return new object pool's object.
     */
    T create();

    /**
     * A validation hook which will be called when an object from the object pool is taken
     * in order to be given to the calling application. Validation on take is not
     * always a required operation and the implementation of this method may simply
     * always return {@code true}.
     *
     * <p>If there is a particular initialization/activation which needs to be done
     * for this object, it could be done in this method.
     *
     * @see #validateOnRestore(Object)
     *
     * @param obj an object which is taken from the object pool and which is to be given
     *            to the calling application
     * @return {@code true} if the validation is successful {@code false} otherwise
     */
    boolean validateOnTake(T obj);

    /**
     * A validation hook which will be called when an object which has been taken
     * before that from the object pool is about to be restored (returned) back to the object pool.
     * Validation on restore is recommended operation and the exact implementation of this
     * method will depend on the concrete object pool object's type and semantics.
     *
     * <p>If there is a particular passivation which needs to be done for this
     * object, it could be done in this method.
     *
     * @see #validateOnTake(Object)
     *
     * @param obj an object which has been taken before that from this object pool and which is now
     *            to be restored to the object pool
     * @return {@code true} if the validation is successful {@code false} otherwise
     */
    boolean validateOnRestore(T obj);

    /**
     * A method which will be called when an object from the object pool needs to be destroyed,
     * which may happen after a validation error or when the object pool is shrinking its size
     * or terminating. The simplest implementation of this method may do nothing, however
     * if there are any allocated resources associated with this object, like network
     * connections or similar, this will be the best place where these resources could be
     * released.
     *
     * @param obj the object which is to be destroyed
     */
    void destroy(T obj);
}
