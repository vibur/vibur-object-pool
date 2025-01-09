/**
 * Copyright 2014 Simeon Malchev
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
 * An instance of this interface can be supplied to the pool at its creation time, and its methods will be called
 * upon calling the pool {@code take} and {@code restore} operations.
 *
 * @author Simeon Malchev
 */
public interface Listener<T> {

    void onTake(T object);

    void onRestore(T object, boolean valid);
}

