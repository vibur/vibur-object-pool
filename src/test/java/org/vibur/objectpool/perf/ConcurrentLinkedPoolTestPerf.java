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

package org.vibur.objectpool.perf;

import org.vibur.objectpool.ConcurrentLinkedPool;
import org.vibur.objectpool.NonValidatingPoolService;
import org.vibur.objectpool.SimpleObjectFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Simeon Malchev
 */
public class ConcurrentLinkedPoolTestPerf {

    private static final int INITIAL_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final int ITERATIONS = 10000;
    private static final int TIMEOUT = 5000;
    private static final int THREADS_COUNT = 200;
    private static final boolean FAIR = true;

    public static void main(String[] args) {

        // Creates a pool with INITIAL_SIZE and MAX_SIZE, and starts THREADS_COUNT threads where each thread
        // executes ITERATIONS times take and then immediately restore operation on an object from the pool.
        // Each take has TIMEOUT in ms and the number of unsuccessful takes is recorded.
        // Measures and reports the total time taken in ms.

        final NonValidatingPoolService<Object> pool = new ConcurrentLinkedPool<Object>(
                new SimpleObjectFactory(), INITIAL_SIZE, MAX_SIZE, FAIR);

        long start = System.currentTimeMillis();
        final AtomicInteger unsuccessful = new AtomicInteger(0);
        Runnable r = new Runnable() {
            public void run() {
                for (int i = 0; i < ITERATIONS; i++) {
                    Object obj = pool.tryTake(TIMEOUT, TimeUnit.MILLISECONDS);
                    if (obj != null)
                        pool.restore(obj);
                    else
                        unsuccessful.incrementAndGet();
                }
            }
        };

        int threadsCount = THREADS_COUNT;
        Thread[] threads = new Thread[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
            threads[i] = new Thread(r);
            threads[i].start();
        }
        for (int i = 0; i < threadsCount; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(String.format("Total execution time %dms, unsuccessful takes %d.",
            (System.currentTimeMillis() - start), unsuccessful.get()));

        pool.terminate();
    }
}
