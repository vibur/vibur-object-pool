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

/**
 * The default implementation of the {@link Reducer}. The reduction is based on
 * comparison of the ratio between the number of taken objects from the object pool and the number of
 * available objects in the object pool (for a given period of time) with a given threshold value.
 *
 * @author Simeon Malchev
 */
public class DefaultReducer implements Reducer {

    private final float takenRatio;
    private final float reduceRatio;

    private long prevTakenCount = 0;

    /**
     * Creates a new {@code DefaultReducer} with default values for
     * {@code takenRatio = 0.90f} and {@code reduceRatio = 0.10f}.
     *
     * @throws NullPointerException if {@code poolService} is null
     */
    public DefaultReducer() {
        this(0.90f, 0.10f);
    }

    /**
     * Creates a new {@code DefaultReducer}.
     *
     * @param takenRatio        the ratio between the taken objects from the object pool and the available
     *                          objects in the object pool (measured for the given period of time)
     * @param reduceRatio       the ratio by which the number of available (created) in the object pool
     *                          objects is to be reduced if the above {@code takenRatio} threshold
     *                          is hit
     * @throws IllegalArgumentException if the following holds:<br>
     *         {@code takenRatio < 0.0f || reduceRatio < 0.0f || reduceRatio > 1.0f}
     * @throws NullPointerException if {@code poolService} is null
     */
    public DefaultReducer(float takenRatio, float reduceRatio) {
        if (takenRatio < 0.0f || reduceRatio < 0.0f || reduceRatio > 1.0f)
            throw new IllegalArgumentException();

        this.takenRatio = takenRatio;
        this.reduceRatio = reduceRatio;
    }

    /** {@inheritDoc} */
    @Override
    public int reduceBy(BasePoolService poolService) {
        int reduction = 0;
        // quick exit if we're already at the minimal pool size
        if (poolService.createdTotal() == poolService.initialSize())
            return reduction;

        long takenCount = poolService.takenCount();
        float takenInInterval = takenCount - prevTakenCount;
        prevTakenCount = takenCount;
        int remainingCreated = poolService.remainingCreated();
        if (takenInInterval / remainingCreated < takenRatio) {
            reduction = (int) (remainingCreated * reduceRatio);
        }
        return reduction;
    }
}
