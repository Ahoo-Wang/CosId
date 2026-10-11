/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.segment;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.util.Clock;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class SegmentChainIdPrefetchTest {
    
    @Test
    void hungryPrefetchShouldNotExceedMaxPrefetchDistance() {
        PrefetchWorkerExecutorService executorService = new PrefetchWorkerExecutorService(Duration.ofHours(1), 1, false);
        try {
            IdSegmentDistributor.Atomic distributor = new IdSegmentDistributor.Atomic(10);
            SegmentChainId segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, 16, distributor, executorService);
            SegmentChainId.PrefetchJob prefetchJob = segmentChainId.getPrefetchJob();
            
            for (int i = 0; i < 20; i++) {
                prefetchJob.setHungerTime(Clock.SYSTEM.secondTime());
                prefetchJob.prefetch();
                Assertions.assertTrue(prefetchJob.getPrefetchDistance() <= 16);
            }
            
            Assertions.assertEquals(16, prefetchJob.getPrefetchDistance());
            Assertions.assertEquals(16, segmentChainId.getMaxPrefetchDistance());
        } finally {
            executorService.shutdown();
        }
    }
    
    @Test
    void defaultMaxPrefetchDistance() {
        PrefetchWorkerExecutorService executorService = new PrefetchWorkerExecutorService(Duration.ofHours(1), 1, false);
        try {
            SegmentChainId segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new IdSegmentDistributor.Atomic(), executorService);
            
            Assertions.assertEquals(SegmentChainId.DEFAULT_MAX_PREFETCH_DISTANCE, segmentChainId.getMaxPrefetchDistance());
        } finally {
            executorService.shutdown();
        }
    }
    
    @Test
    void maxPrefetchDistanceMustNotBeLessThanSafeDistance() {
        PrefetchWorkerExecutorService executorService = new PrefetchWorkerExecutorService(Duration.ofHours(1), 1, false);
        try {
            Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SegmentChainId(TIME_TO_LIVE_FOREVER, 4, 2, new IdSegmentDistributor.Atomic(), executorService));
        } finally {
            executorService.shutdown();
        }
    }
}
