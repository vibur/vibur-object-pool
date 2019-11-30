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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link ConcurrentLinkedQueue} based implementation of {@link ConcurrentCollection}.
 *
 * @author Simeon Malchev
 * @param <T> the type of objects held in this {@code ConcurrentCollection}
 */
public class ConcurrentLinkedQueueCollection<T> implements ConcurrentCollection<T> {

    private final Queue<T> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void offerFirst(T object) {
        offerLast(object);
    }

    @Override
    public void offerLast(T object) {
        queue.add(object);
    }

    @Override
    public T pollFirst() {
        return queue.poll();
    }

    @Override
    public T pollLast() {
        return pollFirst();
    }

    @Override
    public int size() {
        return queue.size();
    }
}
