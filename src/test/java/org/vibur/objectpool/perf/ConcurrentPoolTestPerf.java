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

import org.vibur.objectpool.ConcurrentPool;
import org.vibur.objectpool.PoolService;
import org.vibur.objectpool.SimpleObjectFactory;
import org.vibur.objectpool.util.ConcurrentLinkedDequeCollection;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Simeon Malchev
 */
public class ConcurrentPoolTestPerf {

    // pool metrics:
    private static final int INITIAL_SIZE = 50;
    private static final int MAX_SIZE = 200;
    private static final long TIMEOUT_MS = 2000;
    private static final boolean FAIR = false;

    // threads metrics:
    private static final int ITERATIONS = 100;
    private static final int THREADS_COUNT = 500;
    private static final long DO_WORK_FOR_MS = 2;

    public static void main(String[] args) throws InterruptedException {

        // Creates a ConcurrentLinkedPool with an INITIAL_SIZE and a MAX_SIZE, and starts a THREADS_COUNT threads
        // where each thread executes ITERATIONS times the following code:
        //
        //     Object obj = pool.tryTake(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        //     doWork(DO_WORK_FOR_MS);
        //     pool.restore(obj);
        //
        // Each tryTake() call has a TIMEOUT_MS and the number of unsuccessful takes is recorded.
        // Measures and reports the total time taken by the test in ms.

        PoolService<Object> pool = new ConcurrentPool<>(new ConcurrentLinkedDequeCollection<>(),
                new SimpleObjectFactory(), INITIAL_SIZE, MAX_SIZE, FAIR, null);

        AtomicInteger errors = new AtomicInteger(0);

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch readySignal = new CountDownLatch(THREADS_COUNT);
        CountDownLatch doneSignal = new CountDownLatch(THREADS_COUNT);

        for (int i = 0; i < THREADS_COUNT; i++) {
            Thread thread = new Thread(new Worker(pool, errors, DO_WORK_FOR_MS, TIMEOUT_MS, readySignal, startSignal, doneSignal));
            thread.start();
        }

        readySignal.await();
        long start = System.nanoTime();
        startSignal.countDown();
        doneSignal.await();

        System.out.println(String.format("Total execution time %f ms, unsuccessful takes %d.",
                (System.nanoTime() - start) * 0.000_001, errors.get()));

        pool.terminate();
    }

    private static class Worker implements Runnable {
        private final PoolService<Object> pool;
        private final AtomicInteger errors;
        private final long millis;
        private final long timeout;

        private final CountDownLatch readySignal;
        private final CountDownLatch startSignal;
        private final CountDownLatch doneSignal;

        private Worker(PoolService<Object> pool, AtomicInteger errors, long millis, long timeout,
                       CountDownLatch readySignal, CountDownLatch startSignal, CountDownLatch doneSignal) {
            this.pool = pool;
            this.errors = errors;
            this.millis = millis;
            this.timeout = timeout;
            this.startSignal = startSignal;
            this.readySignal = readySignal;
            this.doneSignal = doneSignal;
        }

        @Override
        public void run() {
            try {
                readySignal.countDown();
                startSignal.await();

                for (int i = 0; i < ITERATIONS; i++) {
                    Object obj = pool.tryTake(timeout, TimeUnit.MILLISECONDS);
                    if (obj != null) {
                        doWork(millis);
                        pool.restore(obj);
                    }
                    else
                        errors.incrementAndGet();
                }
            } catch (InterruptedException ignored) {
                errors.incrementAndGet();
            } finally {
                doneSignal.countDown();
            }
        }
    }

    private static void doWork(long millis) {
        if (millis <= 0)
            return;

        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) { }
    }
}
