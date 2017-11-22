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

package org.vibur.objectpool.util;

import org.junit.After;
import org.junit.Test;
import org.vibur.objectpool.ConcurrentPool;
import org.vibur.objectpool.PoolService;
import org.vibur.objectpool.SimpleObjectFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simeon Malchev
 */
public class SamplingPoolReducerTest {

    private PoolService<Object> pool = null;

    @After
    public void tearDown() {
        if (pool != null )
            pool.terminate();
        pool = null;
    }

    @Test
    public void testPoolShrinking() throws InterruptedException {
        pool = new ConcurrentPool<>(new ConcurrentLinkedDequeCollection<>(), new SimpleObjectFactory(), 10, 100, false);

        // tests the initial pool state
        assertEquals(10, pool.initialSize());
        assertEquals(100, pool.maxSize());

        assertEquals(10, pool.createdTotal());
        assertEquals(10, pool.remainingCreated());
        assertEquals(100, pool.remainingCapacity());
        assertEquals(0, pool.taken());

        // takes 90 objects and test
        Object[] objs = new Object[90];
        for (int i = 0; i < 90; i++) {
            objs[i] = pool.take();
            assertNotNull(objs[i]);
        }
        assertEquals(90, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(10, pool.remainingCapacity());
        assertEquals(90, pool.taken());

        // restores 30 objects and test
        for (int i = 0; i < 30; i++) {
            pool.restore(objs[i]);
        }
        assertEquals(90, pool.createdTotal());
        assertEquals(30, pool.remainingCreated());
        assertEquals(40, pool.remainingCapacity());
        assertEquals(60, pool.taken());

        // creates, starts and then terminates the pool reducer
        final CountDownLatch finishLatch = new CountDownLatch(2);
        ThreadedPoolReducer poolReducer = new SamplingPoolReducer(pool, 400, TimeUnit.MILLISECONDS, 500) {
            @Override
            protected void afterReduce(int reduction, int reduced, Throwable thrown) {
                super.afterReduce(reduction, reduced, thrown);
                finishLatch.countDown();
            }
        };
        poolReducer.start();
        finishLatch.await();
        poolReducer.terminate();

        // Tests the pool metrics after the reducer was called 2 times.
        // Maximum allowed reduction of 20% of 90 will apply on the first call,
        // and on the second call the reduction will be 12, as this is the number
        // of remaining created elements in the pool (smaller than 20% of 72 which is 14.4),
        // i.e. 90 - 18 - 12 = 60 elements created total.
        assertEquals(60, pool.createdTotal());
        assertEquals(0, pool.remainingCreated());
        assertEquals(40, pool.remainingCapacity());
        assertEquals(60, pool.taken());
    }
}
