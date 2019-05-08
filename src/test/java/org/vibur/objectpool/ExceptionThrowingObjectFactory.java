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

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * @author Simeon Malchev
 */
public class ExceptionThrowingObjectFactory implements PoolObjectFactory<Object> {

    private static Unsafe unsafe;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    boolean throwInReadyToTake = false;
    boolean throwInReadyToRestore = false;

    @Override
    public Object create() {
        return new Object();
    }

    @Override
    public boolean readyToTake(Object obj) {
        if (throwInReadyToTake) {
            unsafe.throwException(new Exception("undeclared checked exception thrown for testing purposes"));
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
