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

package vibur.object_pool.util;

import vibur.object_pool.BasePoolService;

import java.util.concurrent.CountDownLatch;

/**
 * @author Simeon Malchev
 */
public class SynchronizedDefaultReducer extends DefaultReducer {

    private CountDownLatch reductionLatch;
    private CountDownLatch startLatch;

    public SynchronizedDefaultReducer(CountDownLatch startLatch, CountDownLatch reductionLatch) {
        this.startLatch = startLatch;
        this.reductionLatch = reductionLatch;
    }

    @Override
    public int reduceBy(BasePoolService poolService) {
        // wait to be enabled by the main unit test thread
        try {
            startLatch.await();
        } catch (InterruptedException ignored) {  }

        int reduction = 0;
        if (reductionLatch.getCount() > 0)
            reduction = super.reduceBy(poolService);
        reductionLatch.countDown();
        return reduction;
    }
}
