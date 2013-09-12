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

package org.vibur.objectpool.util;

/**
 * @author Simeon Malchev
 */
public interface PoolReducer {

    /**
     * Starts this pool reducer, which starts its underlying daemon thread.
     *
     * @exception IllegalThreadStateException if this pool reducer is started more then once
     */
    void start();

    /**
     * Tests if this pool reducer is alive. A pool reducer is alive if it has
     * been started and has not yet been terminated, more precisely if its
     * underlying daemon thread has not yet died.
     *
     * @return true if terminated, false otherwise
     */
    boolean isAlive();

    /**
     * Terminates this pool reducer, which terminates its underlying daemon thread.
     * Once terminated the pool reducer cannot be more revived.
     */
    void terminate();
}
