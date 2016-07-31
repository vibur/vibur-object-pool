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

package org.vibur.objectpool;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simeon Malchev
 */
public class ConcurrentLinkedPoolTest {

    private PoolService<Object> clp = null;

    @After
    public void tearDown() throws Exception {
        if (clp != null )
            clp.terminate();
        clp = null;
    }

    @Test
    public void testSimpleTakes() throws Exception {
        clp = new ConcurrentLinkedPool<>(
                new SimpleObjectFactory(), 1, 3, false);

        Object obj1 = clp.take();
        Object obj2 = clp.take();
        Object obj3 = clp.take();
        Object obj4 = clp.tryTake();

        assertNotNull(obj1);
        assertNotNull(obj2);
        assertNotNull(obj3);
        assertNull(obj4);

        clp.restore(obj1);
        clp.restore(obj2);
        clp.restore(obj3);
        try {
            clp.restore(null);
            fail();
        } catch (NullPointerException ignored) {
        }
    }

    @Test
    public void testSimpleMetrics() throws Exception {
        clp = new ConcurrentLinkedPool<>(
                new SimpleObjectFactory(), 1, 10, false);

        // tests the initial pool state
        assertFalse(clp.isTerminated());
        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(1, clp.createdTotal());
        assertEquals(1, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // takes one object and test
        Object obj1 = clp.take();
        assertNotNull(obj1);
        assertEquals(1, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(9, clp.remainingCapacity());
        assertEquals(1, clp.taken());

        // restores one object and test
        clp.restore(obj1);
        assertEquals(1, clp.createdTotal());
        assertEquals(1, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // takes all objects and test
        Object[] objs = new Object[10];
        for (int i = 0; i < 10; i++) {
            objs[i] = clp.take();
            assertNotNull(objs[i]);
        }
        obj1 = clp.tryTake();
        assertNull(obj1);
        assertEquals(10, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(0, clp.remainingCapacity());
        assertEquals(10, clp.taken());

        // restores the first 6 objects and test
        for (int i = 0; i < 6; i++) {
            clp.restore(objs[i]);
        }
        assertEquals(10, clp.createdTotal());
        assertEquals(6, clp.remainingCreated());
        assertEquals(6, clp.remainingCapacity());
        assertEquals(4, clp.taken());

        // restores the remaining 4 objects and test
        for (int i = 6; i < 10; i++) {
            clp.restore(objs[i]);
        }
        assertEquals(10, clp.createdTotal());
        assertEquals(10, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // terminates the pool and test
        clp.terminate();
        assertTrue(clp.isTerminated());
        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(0, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(0, clp.remainingCapacity());
        assertEquals(0, clp.taken());
    }

    @Test
    public void testPoolReductions() throws Exception {
        clp = new ConcurrentLinkedPool<>(
                new SimpleObjectFactory(), 1, 10, false);

        // takes all objects and test
        Object[] objs = new Object[10];
        for (int i = 0; i < 10; i++) {
            objs[i] = clp.take();
            assertNotNull(objs[i]);
        }
        Object obj1 = clp.tryTake();
        assertNull(obj1);

        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(10, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(0, clp.remainingCapacity());
        assertEquals(10, clp.taken());

        // restores all objects and test
        for (int i = 0; i < 10; i++) {
            clp.restore(objs[i]);
        }
        assertEquals(10, clp.createdTotal());
        assertEquals(10, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // reduce the number of the created objects in the pool BY 5 and test
        int reduction = clp.reduceCreatedBy(5, false);
        assertEquals(5, reduction);

        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(5, clp.createdTotal());
        assertEquals(5, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // now takes again all objects
        for (int i = 0; i < 10; i++) {
            objs[i] = clp.take();
            assertNotNull(objs[i]);
        }
        obj1 = clp.tryTake();
        assertNull(obj1);
        // then restores again all objects and test
        for (int i = 0; i < 10; i++) {
            clp.restore(objs[i]);
        }

        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(10, clp.createdTotal());
        assertEquals(10, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // reduce the number of the created objects in the pool TO 3 and test
        reduction = clp.reduceCreatedTo(3, false);
        assertEquals(7, reduction);

        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(3, clp.createdTotal());
        assertEquals(3, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // drain all created objects from the pool and test
        int drained = clp.drainCreated();
        assertEquals(3, drained);

        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(0, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(0, clp.taken());

        // now takes 5 objects and test
        for (int i = 0; i < 5; i++) {
            objs[i] = clp.take();
            assertNotNull(objs[i]);
        }
        assertEquals(1, clp.initialSize());
        assertEquals(10, clp.maxSize());

        assertEquals(5, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(5, clp.remainingCapacity());
        assertEquals(5, clp.taken());
    }

    @Test
    public void testNoValidations() throws Exception {
        clp = new ConcurrentLinkedPool<>(
                new SimpleObjectFactory(), 1, 2, false);

        // takes one object and test
        Object obj1 = clp.take();
        assertNotNull(obj1);
        // takes second object and test
        Object obj2 = clp.take();
        assertNotNull(obj2);
        // tries to take third object and test
        Object obj3 = clp.tryTake();
        assertNull(obj3);

        clp.restore(obj1);
        clp.restore(obj2);

        // this pool doesn't provide any validation of the restored objects and allows
        // an object which is not taken from the pool to be restored to it,
        // even if the pool remainingCapacity() will increase above its maxSize() and createdTotal().
        clp.restore(new Object());

        assertEquals(3, clp.remainingCapacity());
        assertEquals(2, clp.createdTotal());
        assertEquals(2, clp.maxSize());
    }
}
