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

package vibur.object_pool;

import java.util.concurrent.TimeUnit;

/**
 * @author Simeon Malchev
 */
public class ConcurrentHolderLinkedPoolTestPerf {

    public static void main(String[] args) {

        // Creates pool with maxSize 20, and starts 200 threads where each thread is trying
        // to take/restore 10000 objects from the pool. Measures the time taken in milliseconds.

        long start = System.currentTimeMillis();
        final HolderValidatingPoolService<Object> chlp = new ConcurrentHolderLinkedPool<Object>(
                new SimpleObjectFactory(), 10, 20, false);
        Runnable r = new Runnable() {
            public void run() {
                for (int i = 0; i < 10000; i++) {
                    Holder<Object> obj = chlp.tryTake(5000, TimeUnit.MILLISECONDS);
                    chlp.restore(obj);
                }
            }
        };

        int threadNum = 200;
        Thread[] threads = new Thread[threadNum];
        for (int i = 0; i < threadNum; i++) {
            threads[i] = new Thread(r);
            threads[i].start();
        }
        for (int i = 0; i < threadNum; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Execution time millis = " + (System.currentTimeMillis() - start));
    }
}
