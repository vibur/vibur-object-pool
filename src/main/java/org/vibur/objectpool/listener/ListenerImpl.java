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

package org.vibur.objectpool.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Simeon Malchev
 */
public class ListenerImpl<T> implements Listener<T> {

    private final Set<T> taken;

    public ListenerImpl() {
        taken = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());
    }

    public ListenerImpl(int initialCapacity) {
        taken = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>(initialCapacity));
    }

    public void onTake(T object) {
        taken.add(object);
    }

    public void onRestore(T object) {
        taken.remove(object);
    }

    public List<T> getTaken() {
        return new ArrayList<T>(taken);
    }
}
