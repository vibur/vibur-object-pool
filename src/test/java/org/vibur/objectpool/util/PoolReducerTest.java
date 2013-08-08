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
import org.vibur.objectpool.ConcurrentLinkedPool;
import org.vibur.objectpool.NonValidatingPoolService;
import org.vibur.objectpool.SimpleObjectFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Simeon Malchev
 */
public class PoolReducerTest {

    private NonValidatingPoolService<Object> clp = null;

    @After
    public void tearDown() throws Exception {
        if (clp != null )
            clp.terminate();
        clp = null;
    }

    @Test
    public void testPoolShrinking() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch reductionLatch = new CountDownLatch(3);
        clp = new ConcurrentLinkedPool<Object>(new SimpleObjectFactory(), 10, 100, false);

        PoolReducer poolReducer = new PoolReducer(
                clp, new SynchronizedDefaultReducer(startLatch, reductionLatch),
                100, TimeUnit.MILLISECONDS);
        poolReducer.start();

        // tests the initial pool state
        assertEquals(10, clp.initialSize());
        assertEquals(100, clp.maxSize());

        assertEquals(10, clp.createdTotal());
        assertEquals(10, clp.remainingCreated());
        assertEquals(100, clp.remainingCapacity());
        assertEquals(0, clp.taken());
        assertEquals(0, clp.takenCount());

        // takes 90 objects and test
        Object[] objs = new Object[90];
        for (int i = 0; i < 90; i++) {
            objs[i] = clp.take();
            assertNotNull(objs[i]);
        }
        assertEquals(90, clp.createdTotal());
        assertEquals(0, clp.remainingCreated());
        assertEquals(10, clp.remainingCapacity());
        assertEquals(90, clp.taken());
        assertEquals(90, clp.takenCount());

        // restores 90 objects and test
        for (int i = 0; i < 90; i++) {
            clp.restore(objs[i]);
        }
        assertEquals(90, clp.createdTotal());
        assertEquals(90, clp.remainingCreated());
        assertEquals(100, clp.remainingCapacity());
        assertEquals(0, clp.taken());
        assertEquals(90, clp.takenCount());

        // enable the pool reducer
        startLatch.countDown();
        // await for 3 calls to the pool reducer to be done:
        // on the first call no reduction will happen
        // on the second call a reduction of 9 objects should happen
        // on the third call a reduction of 8 objects should happen
        // the total reduction should be 17, that's why the created total will drop from 90 to 73.
        reductionLatch.await();

        // tests the pool metrics after the reducer was called 3 times
        assertEquals(73, clp.createdTotal());
        assertEquals(73, clp.remainingCreated());
        assertEquals(100, clp.remainingCapacity());
        assertEquals(0, clp.taken());
        assertEquals(90, clp.takenCount());
    }
}
