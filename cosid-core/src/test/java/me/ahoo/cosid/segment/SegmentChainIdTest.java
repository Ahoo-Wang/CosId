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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.segment.concurrent.AffinityJob;
import me.ahoo.cosid.segment.concurrent.PrefetchWorker;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

class SegmentChainIdTest {

    @Test
    void currentShouldStartAtUnavailableRootChain() {
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new IdSegmentDistributor.Atomic(2), new NoopPrefetchWorkerExecutorService());

        assertEquals(IdSegmentChain.ROOT_VERSION, generator.getHead().getVersion());
        assertSame(generator.getHead(), generator.current());
        assertFalse(generator.current().isAvailable());
    }

    @Test
    void generateShouldAppendChainAndForwardHeadThroughAvailableSegments() {
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new IdSegmentDistributor.Atomic(2), new NoopPrefetchWorkerExecutorService());

        assertEquals(1, generator.generate());
        assertEquals(2, generator.generate());
        assertEquals(3, generator.generate());
        assertEquals(4, generator.generate());

        assertEquals(0, generator.getHead().getVersion());
        assertEquals(4, generator.getHead().getMaxId());
        assertTrue(generator.getHead().isOverflow());
        assertEquals(5, generator.generate());
        assertEquals(1, generator.getHead().getVersion());
    }

    @Test
    void nextIdSegmentChainShouldCreateMergedChainWithExpectedVersionAndRange() {
        IdSegmentDistributor distributor = new IdSegmentDistributor.Atomic(10);
        IdSegmentChain root = IdSegmentChain.newRoot(false);

        IdSegmentChain chain = distributor.nextIdSegmentChain(root, 3, TIME_TO_LIVE_FOREVER);

        assertEquals(0, chain.getVersion());
        assertEquals(0, chain.getOffset());
        assertEquals(30, chain.getStep());
        assertEquals(30, chain.getMaxId());
        assertEquals(3, chain.gap(chain, distributor.getStep()));
    }

    @Test
    void idSegmentChainShouldSetNextOnlyOnceAndRejectExpiredNextWhenResetIsForbidden() {
        IdSegmentChain root = IdSegmentChain.newRoot(false);
        IdSegmentChain first = new IdSegmentChain(root, new DefaultIdSegment(10, 10), false);
        IdSegmentChain expired = new IdSegmentChain(root, new DefaultIdSegment(5, 5), false);

        assertTrue(root.trySetNext(ignored -> first));
        assertFalse(root.trySetNext(ignored -> expired));
        assertSame(first, root.getNext());
        assertThrows(NextIdSegmentExpiredException.class, () -> first.setNext(expired));
    }

    @Test
    void multipleInstancesSharingDistributorShouldGenerateGloballyUniqueIdsWithPossibleGaps() {
        IdSegmentDistributor distributor = new IdSegmentDistributor.Atomic(2);
        SegmentChainId first = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, distributor, new NoopPrefetchWorkerExecutorService());
        SegmentChainId second = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, distributor, new NoopPrefetchWorkerExecutorService());
        long[] ids = new long[]{
            first.generate(),
            second.generate(),
            first.generate(),
            second.generate()
        };

        Arrays.sort(ids);

        assertArrayEquals(new long[]{1, 2, 5, 6}, ids);
    }

    @Test
    void constructorShouldRejectInvalidTtlAndSafeDistance() {
        IdSegmentDistributor distributor = new IdSegmentDistributor.Atomic(2);
        NoopPrefetchWorkerExecutorService executor = new NoopPrefetchWorkerExecutorService();

        assertEquals(
            "Illegal idSegmentTtl parameter:[0].",
            assertThrows(IllegalArgumentException.class, () -> new SegmentChainId(0, 2, distributor, executor)).getMessage()
        );
        assertEquals(
            "The safety distance must be greater than 0.",
            assertThrows(IllegalArgumentException.class, () -> new SegmentChainId(TIME_TO_LIVE_FOREVER, 0, distributor, executor)).getMessage()
        );
    }

    @Test
    void generateShouldRemainUniqueUnderSmallConcurrentLoad() {
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 4, new IdSegmentDistributor.Atomic(64), new NoopPrefetchWorkerExecutorService());

        new ConcurrentGenerateSpec(4, 256, Duration.ofSeconds(5), generator).verify();
    }

    private static final class NoopPrefetchWorkerExecutorService extends PrefetchWorkerExecutorService {
        private final PrefetchWorker worker = new NoopPrefetchWorker();

        private NoopPrefetchWorkerExecutorService() {
            super(Duration.ofDays(1), 1, false);
        }

        @Override
        public void submit(AffinityJob affinityJob) {
            affinityJob.setPrefetchWorker(worker);
        }
    }

    private static final class NoopPrefetchWorker implements PrefetchWorker {
        @Override
        public String getName() {
            return "noop-prefetch-worker";
        }

        @Override
        public void submit(AffinityJob affinityJob) {
        }

        @Override
        public void cancel(AffinityJob affinityJob) {
        }

        @Override
        public void wakeup(AffinityJob affinityJob) {
        }

        @Override
        public void shutdown() {
        }
    }
}
