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
 * An <strong>unique id</strong> based implementation of {@link Holder} interface. Allows for very
 * efficient {@code hashCode()} and {@code equals()} methods implementation, which makes it an excellent
 * choice for {@code HashMap} keys.
 *
 * <p>This class can be extended by other classes which augment it with additional "state" information.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects wrapped in this object holder
 */
public class IdBasedHolder<T> implements Holder<T> {

    private final long uniqueId;
    private final T value;

    public IdBasedHolder(long uniqueId, T value) {
        if (value == null)
            throw new NullPointerException();
        this.uniqueId = uniqueId;
        this.value = value;
    }

    public T value() { return value; }

    public int hashCode() {
        return (int) uniqueId;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return uniqueId == ((IdBasedHolder) obj).uniqueId;
    }
}
