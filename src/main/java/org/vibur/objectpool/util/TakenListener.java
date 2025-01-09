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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An instance of this class can be supplied to the pool at its creation time, and its methods will be called
 * upon calling the pool take and restore operations. This listener can provide a list of all currently taken
 * objects from the pool, which can be useful for testing and debugging purposes.
 *
 * @author Simeon Malchev
 */
public class TakenListener<T> implements Listener<T> {

    private final Set<T> taken;

    public TakenListener() {
        taken = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public TakenListener(int initialCapacity) {
        taken = Collections.newSetFromMap(new ConcurrentHashMap<>(initialCapacity));
    }

    @Override
    public void onTake(T object) {
        taken.add(object);
    }

    @Override
    public void onRestore(T object, boolean valid) {
        taken.remove(object);
    }

    protected T[] getTaken(T[] a) {
        return taken.toArray(a);
    }
}
