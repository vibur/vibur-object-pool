/**
 * Copyright 2016 Simeon Malchev
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
 * Defines the base object pool operations. These operations include various pool metrics,
 * pool termination methods, and support for shrinking (reduction) of the number of allocated
 * on the pool objects.
 *
 * @author Simeon Malchev
 */
public interface BasePool extends AutoCloseable {

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
     * Tries to remove (and destroy) up to {@code reduceBy} objects from the object pool. This method may
     * bring the object pool {@link #createdTotal()} to a number less then its {@link #initialSize()}.
     *
     * @param reduceBy          the desired amount of objects to be removed
     * @param ignoreInitialSize specifies whether the {@link #createdTotal()} may be
     *                          reduced to less than {@link #initialSize()}
     * @return the actual amount of objects removed
     */
    int reduceCreatedBy(int reduceBy, boolean ignoreInitialSize);

    /**
     * Tries to remove (and destroy) such number of objects from the object pool that the number of
     * {@link #createdTotal()} objects in the pool to become equal of {@code reduceTo}. This method may bring
     * the object pool {@link #createdTotal()} to a number smaller than its {@link #initialSize()}.
     *
     * @param reduceTo          the desired amount of created objects to remain in the pool
     * @param ignoreInitialSize specifies whether the {@link #createdTotal()} may be
     *                          reduced to less than {@link #initialSize()}
     * @return the actual amount of objects removed
     */
    int reduceCreatedTo(int reduceTo, boolean ignoreInitialSize);

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
     * A synonym for {@link #terminate()}. Overrides the {@link AutoCloseable}'s method in order to overrule
     * the throwing of a checked {@code Exception}.
     */
    @Override
    void close();
}
