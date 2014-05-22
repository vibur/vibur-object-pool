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

package org.vibur.objectpool.validator;

import org.vibur.objectpool.util.Holder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Simeon Malchev
 */
public class ConcurrentMapHolderValidator<T> implements Validator<Holder<T>> {

    private final ConcurrentMap<Holder<T>, T> map;

    public ConcurrentMapHolderValidator() {
        map = new ConcurrentHashMap<Holder<T>, T>();
    }

    public ConcurrentMapHolderValidator(int initialCapacity) {
        map = new ConcurrentHashMap<Holder<T>, T>(initialCapacity);
    }

    public void add(Holder<T> object) {
        map.put(object, object.value());
    }

    public boolean remove(Holder<T> object) {
        return map.remove(object, object.value());
    }

    public List<Holder<T>> getAll() {
        return new ArrayList<Holder<T>>(map.keySet());
    }
}
