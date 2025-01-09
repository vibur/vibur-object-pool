/**
 * Copyright 2019 Simeon Malchev
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
 * @author Simeon Malchev
 */
public class ExceptionThrowingObjectFactory implements PoolObjectFactory<Object> {

    boolean throwInReadyToTake = false;
    boolean throwInReadyToRestore = false;

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAny(Throwable t) throws T {
        throw (T) t;
    }

    @Override
    public Object create() {
        return new Object();
    }

    @Override
    public boolean readyToTake(Object obj) {
        if (throwInReadyToTake) {
            throwAny(new Exception("undeclared checked exception thrown for testing purposes"));
        }
        return true;
    }

    @Override
    public boolean readyToRestore(Object obj) {
        if (throwInReadyToRestore) {
            throw new RuntimeException("runtime exception thrown for testing purposes");
        }
        return true;
    }

    @Override
    public void destroy(Object obj) {
        // do nothing
    }
}
