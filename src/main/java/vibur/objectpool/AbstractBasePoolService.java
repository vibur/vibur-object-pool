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

package vibur.objectpool;

/**
 * Base abstract class which is to be extended and which provides common functionality for
 * both validating and non-validating object pool's implementations.
 *
 * @author Simeon Malchev
 */
public abstract class AbstractBasePoolService implements BasePoolService {

    /** {@inheritDoc} */
    public int taken() {
        return isTerminated() ? maxSize() : maxSize() - remainingCapacity();
    }

    /** {@inheritDoc} */
    public int remainingCreated() {
        return isTerminated() ? 0 : createdTotal() - taken(); // faster than calling {@code available.size())
    }

    /** {@inheritDoc} */
    public int drainCreated() {
        return reduceCreated(Integer.MAX_VALUE);
    }

    /** {@inheritDoc} */
    protected void finalize() {
        terminate();
    }

    /** {@inheritDoc} */
    public String toString() {
        return super.toString() + (isTerminated() ? "[terminated]"
                : "[remainingCreated = " + remainingCreated() + "]");
    }
}
