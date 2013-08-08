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

import org.junit.Test;
import vibur.object_pool.BasePoolService;

import static junit.framework.Assert.assertEquals;

/**
 * @author Simeon Malchev
 */
public class DefaultReducerTest {

    @Test
    public void testDefault_ReduceBy() throws Exception {
        DefaultReducer defaultPoolReducer = new DefaultReducer();
        BasePoolService poolService = new PoolServiceTestSkeleton() {
            @Override
            public long takenCount() {
                return 10;
            }

            @Override
            public int remainingCreated() {
                return 90;
            }
        };

        int reduction = defaultPoolReducer.reduceBy(poolService);
        assertEquals(9, reduction);
    }

    @Test
    public void testArbitrary_ReduceBy() throws Exception {
        DefaultReducer defaultPoolReducer = new DefaultReducer(0.5f, 0.2f);
        BasePoolService poolService = new PoolServiceTestSkeleton() {
            @Override
            public long takenCount() {
                return 20;
            }

            @Override
            public int remainingCreated() {
                return 80;
            }
        };

        int reduction = defaultPoolReducer.reduceBy(poolService);
        assertEquals(16, reduction);
    }


    private static abstract class PoolServiceTestSkeleton implements BasePoolService {
        @Override
        public int taken() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int createdTotal() {
            return 100;
        }

        @Override
        public int remainingCapacity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int initialSize() {
            return 1;
        }

        @Override
        public int maxSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int reduceCreated(int reduction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int drainCreated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void terminate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFair() {
            throw new UnsupportedOperationException();
        }
    }
}
