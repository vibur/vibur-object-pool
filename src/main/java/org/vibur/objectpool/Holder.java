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
 * A holder interface which needs to be implemented by the thin wrapper class enclosing
 * the taken from this object pool objects.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects wrapped in this object holder
 */
public interface Holder<T> {

    /**
     * Returns the underlying object held by this Holder.
     *
     * @return the underlying object
     */
    T value();

    /**
     * Return the stack trace of the call with which this object was taken. Useful
     * for testing and debugging purposes.
     *
     * <p>This is an optional operation. If not implemented should simply return {@code null}.
     *
     * @return see above
     */
    StackTraceElement[] getStackTrace();

    /**
     * Return the time in milliseconds since epoch when the {@link #getStackTrace()} was taken. Useful
     * for testing and debugging purposes.
     *
     * <p>This is an optional operation. If not implemented should simply return {@code -1}.
     *
     * @return see above
     */
    long getTime();
}
