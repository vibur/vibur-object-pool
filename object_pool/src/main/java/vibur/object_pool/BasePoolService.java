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
 * Defines base objects object pool's functionality which is to be implemented by the validating
 * and non-validating pools. These validating and non-validating pools will provide
 * appropriate object's take and restore functionality (methods).
 *
 * <p>This object pool may have support for an automated shrinking (reduction) of the number of
 * allocated on the object pool objects.
 *
 * <p>The object pool may support optional fairness with regards to waiting taker threads.
 *
 * @author Simeon Malchev
 */
public interface BasePoolService {

    /**
     * Returns the number of objects taken from this object pool.
     * This number is less than or equal to the object pool's {@link #createdTotal()}.
     * Mainly useful for testing an debugging purposes.
     *
     * @return the number of objects taken from this object pool
     */
    int taken();

    /**
     * Returns the number of remaining created objects which currently exist in this object pool.
     * This number is less than or equal to the object pool's {@link #remainingCapacity()}.
     * Mainly useful for testing an debugging purposes.
     *
     * @return the number of remaining created objects in this object pool
     */
    int remainingCreated();

    /**
     * Returns the total number of created objects which currently exist for this object pool.
     * This number is equal to {@link #taken()} + {@link #remainingCreated()}.
     * Mainly useful for testing an debugging purposes.
     *
     * @return the total number of created objects for this object pool
     */
    int createdTotal();

    /**
     * Returns the remaining capacity of this object pool, i.e. the number of objects which could be
     * taken from this object pool without blocking. It is not guaranteed that all these objects
     * exist at the time of the call (i.e. are already created) in the object pool - some of them
     * might be created on demand upon take requests. Also see {@link #remainingCreated()}.
     * Mainly useful for testing an debugging purposes.
     *
     * @return the object pool's remaining capacity
     */
    int remainingCapacity();

    /**
     * Returns the {@code initialSize} of this object pool.
     *
     * @return the object pool's {@code initialSize}
     */
    int initialSize();

    /**
     * Returns the {@code maxSize} of this object pool.
     *
     * @return the object pool's {@code maxSize}
     */
    int maxSize();


    /**
     * Tries to remove (and destroy) up to {@code reduction} objects from the object pool.
     * Will not bring the object pool's {@link #createdTotal()} to less then its {@link #initialSize()}.
     *
     * @param reduction the desired amount of objects to be removed
     * @return the actual amount of objects removed
     */
    int reduceCreated(int reduction);

    /**
     * Tries to remove (and destroy) as many created objects from this object pool as possible, without
     * bringing the object pool's {@link #createdTotal()} to less then its {@link #initialSize()}.
     *
     * @return the actual amount of objects removed (and destroyed)
     */
    int drainCreated();


    /**
     * Terminates this object pool. Once terminated the object pool cannot be more revived.
     * All take and restore operations called on a terminated object pool should throw
     * an exception or be ignored.
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

    /**
     * Returns the <i>total</i> number of objects taken from this object pool since the
     * creation of the object pool.This count starts from {@code 0} and never decreases.
     * It will stop increasing once the object pool is terminated.
     *
     * <p>Intended for statistical and testing/debugging purposes.
     *
     * @return as described above
     */
    long takenCount();
}
