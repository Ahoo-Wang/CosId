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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SegmentChainIdUnusableNodeTest {
    
    /**
     * With the prefetch stopped, the head's next node already exists but is exhausted. New segments used to be appended
     * after the head only, which failed because the head had a next node, so generation spun forever.
     */
    @Test
    void generatesPastAnExhaustedNodeWhenThePrefetchHasStopped() {
        AtomicIdSegmentDistributor distributor = new AtomicIdSegmentDistributor(10);
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        SegmentChainId chainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 1, distributor, executor);
        chainId.close();
        executor.shutdown();
        
        IdSegmentChain head = chainId.getHead();
        IdSegmentChain exhausted = new IdSegmentChain(head, distributor.nextIdSegment(), false);
        while (!exhausted.isOverflow(exhausted.incrementAndGet())) {
            // use the node up
        }
        head.setNext(exhausted);
        
        long id = assertTimeoutPreemptively(Duration.ofSeconds(5), chainId::generate);
        assertThat(id, greaterThan(exhausted.getMaxId()));
    }
    
    @Test
    void concurrentGenerationWithoutPrefetchIsUniqueAndDoesNotHang() throws InterruptedException {
        AtomicIdSegmentDistributor distributor = new AtomicIdSegmentDistributor(10);
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        SegmentChainId chainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 1, distributor, executor);
        chainId.close();
        executor.shutdown();
        
        int threads = 8;
        int perThread = 10_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    for (int j = 0; j < perThread; j++) {
                        ids.add(chainId.generate());
                    }
                    done.countDown();
                });
            }
            assertThat(done.await(30, TimeUnit.SECONDS), equalTo(true));
        } finally {
            pool.shutdownNow();
        }
        assertThat(ids.size(), equalTo(threads * perThread));
    }
}
