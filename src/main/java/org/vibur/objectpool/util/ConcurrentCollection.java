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

package org.vibur.objectpool.util;

/**
 * A concurrent collection that provides 4 access methods: {@link #addFirst}, {@link #addLast}, {@link #pollFirst},
 * and {@link #pollLast}. If the implementing sub-class delegates the above methods to a Collection that does not
 * have native implementation for {@code addFirst()} and {@code pollLast()} it can implement these methods
 * in the same way as {@code addLast()} and {@code pollFirst()}, respectively.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this {@code ConcurrentCollection}
 */
public interface ConcurrentCollection<T> {

    /**
     * Adds the given {@code object} at the head of the {@code ConcurrentCollection}.
     *
     * @param object the given object
     */
    void addFirst(T object);

    /**
     * Adds the given {@code object} at the tail of the {@code ConcurrentCollection}.
     *
     * @param object the given object
     */
    void addLast(T object);

    /**
     * Polls an {@code object} from the head of the {@code ConcurrentCollection}.
     *
     * @return the head Collection object if available; {@code null} otherwise
     */
    T pollFirst();

    /**
     * Polls an {@code object} from the tail of the {@code ConcurrentCollection}.
     *
     * @return the tail Collection object if available; {@code null} otherwise
     */
    T pollLast();
}
