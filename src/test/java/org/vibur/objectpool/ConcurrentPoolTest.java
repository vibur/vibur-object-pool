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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vibur.objectpool.util.ConcurrentLinkedDequeCollection;
import org.vibur.objectpool.util.ConcurrentLinkedQueueCollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simeon Malchev
 */
public class ConcurrentPoolTest {

    private PoolService<Object> pool = null;

    @AfterEach
    public void tearDown() {
        if (pool != null ) {
            pool.terminate();
        }
        pool = null;
    }

    @Test
    public void testSimpleTakes() {
        pool = new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), new SimpleObjectFactory(), 1, 3, false);

        var obj1 = pool.take();
        var obj2 = pool.take();
        var obj3 = pool.take();
        var obj4 = pool.tryTake();

        assertNotNull(obj1);
        assertNotNull(obj2);
        assertNotNull(obj3);
        assertNull(obj4);

        pool.restore(obj1);
        pool.restore(obj2);
        pool.restore(obj3);

        assertThrows(NullPointerException.class, () -> pool.restore(null));
    }

    @Test
    public void testInterrupted() {
        pool = new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), new SimpleObjectFactory(), 1, 3, false);

        Thread.currentThread().interrupt();
        var obj1 = pool.take();

        assertNull(obj1);
        assertTrue(Thread.interrupted()); // clears the interrupted flag in order to not affect subsequent tests
    }

    @Test
    public void testSimpleMetrics() {
        pool = new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), new SimpleObjectFactory(), 1, 10, false);

        // tests the initial pool state
        assertFalse(pool.isTerminated());
        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(1, pool.createdTotal());
        assertEquals(1, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // takes one object and test
        var obj1 = pool.take();
        assertNotNull(obj1);
        assertEquals(1, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(9, pool.remainingCapacity());
        assertEquals(1, pool.taken());

        // restores one object and test
        pool.restore(obj1);
        assertEquals(1, pool.createdTotal());
        assertEquals(1, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // takes all objects and test
        var objs = new Object[10];
        for (var i = 0; i < 10; i++) {
            objs[i] = pool.take();
            assertNotNull(objs[i]);
        }
        obj1 = pool.tryTake();
        assertNull(obj1);
        assertEquals(10, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(0, pool.remainingCapacity());
        assertEquals(10, pool.taken());

        // restores the first 6 objects and test
        for (var i = 0; i < 6; i++) {
            pool.restore(objs[i]);
        }
        assertEquals(10, pool.createdTotal());
        assertEquals(6, pool.remainingCreated());
        assertEquals(6, pool.remainingCapacity());
        assertEquals(4, pool.taken());

        // restores the remaining 4 objects and test
        for (var i = 6; i < 10; i++) {
            pool.restore(objs[i]);
        }
        assertEquals(10, pool.createdTotal());
        assertEquals(10, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // terminates the pool and test
        pool.terminate();
        assertTrue(pool.isTerminated());
        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(0, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(0, pool.remainingCapacity());
        assertEquals(0, pool.taken());
    }

    @Test
    public void testSimpleMetricsWhenExceptionIsThrownFromObjectFactory() {
        var objectFactory = new ExceptionThrowingObjectFactory();
        pool = new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), objectFactory, 1, 10, false);

        // tests the initial pool state
        assertFalse(pool.isTerminated());
        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(1, pool.createdTotal());
        assertEquals(1, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        objectFactory.throwInReadyToTake = true;
        // tries to take one object and test
        try {
            pool.take();
            fail("Exception expected");
        } catch (Exception e) {
            assertSame(Exception.class, e.getClass());
            assertEquals("undeclared checked exception thrown for testing purposes", e.getMessage());
        }
        assertEquals(0, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        objectFactory.throwInReadyToTake = false;
        // takes one object and test
        var obj1 = pool.take();
        assertNotNull(obj1);
        assertEquals(1, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(9, pool.remainingCapacity());
        assertEquals(1, pool.taken());

        // tries to restore the taken object and test
        objectFactory.throwInReadyToRestore = true;
        try {
            pool.restore(obj1);
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertSame(RuntimeException.class, e.getClass());
            assertEquals("runtime exception thrown for testing purposes", e.getMessage());
        }
        assertEquals(0, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // terminates the pool and test
        pool.terminate();
        assertTrue(pool.isTerminated());
        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(0, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(0, pool.remainingCapacity());
        assertEquals(0, pool.taken());
    }

    @Test
    public void testPoolReductions() {
        pool = new ConcurrentPool<>(new ConcurrentLinkedDequeCollection<>(), new SimpleObjectFactory(), 1, 10, false);

        // takes all objects and test
        var objs = new Object[10];
        for (var i = 0; i < 10; i++) {
            objs[i] = pool.take();
            assertNotNull(objs[i]);
        }
        var obj1 = pool.tryTake();
        assertNull(obj1);

        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(10, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(0, pool.remainingCapacity());
        assertEquals(10, pool.taken());

        // restores all objects and test
        for (var i = 0; i < 10; i++) {
            pool.restore(objs[i]);
        }
        assertEquals(10, pool.createdTotal());
        assertEquals(10, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // reduce the number of the created objects in the pool BY 5 and test
        var reduction = pool.reduceCreatedBy(5, false);
        assertEquals(5, reduction);

        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(5, pool.createdTotal());
        assertEquals(5, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // now takes again all objects
        for (var i = 0; i < 10; i++) {
            objs[i] = pool.take();
            assertNotNull(objs[i]);
        }
        obj1 = pool.tryTake();
        assertNull(obj1);
        // then restores again all objects and test
        for (var i = 0; i < 10; i++) {
            pool.restore(objs[i]);
        }

        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(10, pool.createdTotal());
        assertEquals(10, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // reduce the number of the created objects in the pool TO 3 and test
        reduction = pool.reduceCreatedTo(3, false);
        assertEquals(7, reduction);

        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(3, pool.createdTotal());
        assertEquals(3, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // drain all created objects from the pool and test
        var drained = pool.drainCreated();
        assertEquals(3, drained);

        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(0, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // now takes 5 objects and test
        for (var i = 0; i < 5; i++) {
            objs[i] = pool.take();
            assertNotNull(objs[i]);
        }
        assertEquals(1, pool.initialSize());
        assertEquals(10, pool.maxSize());

        assertEquals(5, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(5, pool.remainingCapacity());
        assertEquals(5, pool.taken());
    }

    @Test
    public void testNoValidations() {
        pool = new ConcurrentPool<>(new ConcurrentLinkedDequeCollection<>(), new SimpleObjectFactory(), 1, 2, false);

        // takes one object and test
        var obj1 = pool.take();
        assertNotNull(obj1);
        // takes second object and test
        var obj2 = pool.take();
        assertNotNull(obj2);
        // tries to take third object and test
        var obj3 = pool.tryTake();
        assertNull(obj3);

        pool.restore(obj1);
        pool.restore(obj2);

        // this pool doesn't provide any validation of the restored objects and allows
        // an object which is not taken from the pool to be restored to it,
        // even if the pool remainingCapacity() will increase above its maxSize() and createdTotal().
        pool.restore(new Object());

        assertEquals(3, pool.remainingCapacity());
        assertEquals(2, pool.createdTotal());
        assertEquals(2, pool.maxSize());
    }

    @Test
    public void testTimeWaited() {
        pool = new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), new SimpleObjectFactory(), 1, 2, false);

        // takes one object and test
        long[] timeWaited = {-1};
        var obj1 = pool.take(timeWaited);

        assertNotNull(obj1);
        assertTrue(timeWaited[0] >= 0);
    }
}
