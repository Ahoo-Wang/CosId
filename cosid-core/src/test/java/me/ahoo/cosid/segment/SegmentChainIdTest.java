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
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;
import me.ahoo.cosid.util.CallTimeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

class SegmentChainIdTest {

    @Test
    void currentShouldStartAtUnavailableRootChain() {
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new AtomicIdSegmentDistributor(2), new NoopPrefetchWorkerExecutorService());

        assertEquals(IdSegmentChain.ROOT_VERSION, generator.getHead().getVersion());
        assertSame(generator.getHead(), generator.current());
        assertFalse(generator.current().isAvailable());
    }

    @Test
    void generateShouldAppendChainAndForwardHeadThroughAvailableSegments() {
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new AtomicIdSegmentDistributor(2), new NoopPrefetchWorkerExecutorService());

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
        IdSegmentDistributor distributor = new AtomicIdSegmentDistributor(10);
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
        IdSegmentDistributor distributor = new AtomicIdSegmentDistributor(2);
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
        IdSegmentDistributor distributor = new AtomicIdSegmentDistributor(2);
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
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 4, new AtomicIdSegmentDistributor(64), new NoopPrefetchWorkerExecutorService());

        new ConcurrentGenerateSpec(4, 256, Duration.ofSeconds(5), generator).verify();
    }

    @Test
    void prefetchShouldBeSubmittedOnFirstGenerateOnly() {
        NoopPrefetchWorkerExecutorService executor = new NoopPrefetchWorkerExecutorService();
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new AtomicIdSegmentDistributor(2), executor);

        assertEquals(0, executor.submitted.get());
        generator.generate();
        generator.generate();
        assertEquals(1, executor.submitted.get());
    }

    @Test
    void closeShouldCancelPrefetchOnceAndKeepGenerating() {
        NoopPrefetchWorkerExecutorService executor = new NoopPrefetchWorkerExecutorService();
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new AtomicIdSegmentDistributor(2), executor);
        assertEquals(1, generator.generate());

        generator.close();
        generator.close();

        assertTrue(generator.isClosed());
        assertEquals(1, executor.worker.cancelled.get());
        assertEquals(2, generator.generate());
        assertEquals(3, generator.generate());
        assertEquals(1, executor.submitted.get());
    }

    @Test
    void generateAfterCloseShouldNotSubmitPrefetch() {
        NoopPrefetchWorkerExecutorService executor = new NoopPrefetchWorkerExecutorService();
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, new AtomicIdSegmentDistributor(2), executor);

        generator.close();
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, generator.generate());
        }

        assertEquals(0, executor.submitted.get());
        assertEquals(0, executor.worker.cancelled.get());
    }

    @Test
    void slowPrefetchWithTimeoutShouldNotStallAnotherChainOnTheSameWorker() throws InterruptedException {
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofMillis(20), 1, false);
        SlowIdSegmentDistributor slowDistributor = new SlowIdSegmentDistributor();
        WorkerCountingIdSegmentDistributor fastDistributor = new WorkerCountingIdSegmentDistributor();
        try {
            SegmentChainId slowChain = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, 2, slowDistributor, executor, CallTimeout.of(Duration.ofMillis(100)));
            SegmentChainId fastChain = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, 2, fastDistributor, executor, CallTimeout.NONE);
            slowChain.generate();
            slowDistributor.slow = true;
            slowChain.getPrefetchJob().hungry();
            fastChain.generate();
            assertSame(slowChain.getPrefetchJob().getPrefetchWorker(), fastChain.getPrefetchJob().getPrefetchWorker());
            int before = fastDistributor.workerCalls.get();

            fastChain.getPrefetchJob().hungry();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (fastDistributor.workerCalls.get() == before && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertTrue(fastDistributor.workerCalls.get() > before, "the fast chain's prefetch did not run");
        } finally {
            slowDistributor.slow = false;
            executor.shutdown();
        }
    }

    private static final class WorkerCountingIdSegmentDistributor extends AtomicIdSegmentDistributor {
        private final AtomicInteger workerCalls = new AtomicInteger();

        private WorkerCountingIdSegmentDistributor() {
            super(2);
        }

        @Override
        public long nextMaxId(long step) {
            if (Thread.currentThread().getName().startsWith("DefaultPrefetchWorker")) {
                workerCalls.incrementAndGet();
            }
            return super.nextMaxId(step);
        }
    }

    @Test
    void prefetchSlowerThanTimeoutShouldBeGivenUp() {
        SlowIdSegmentDistributor distributor = new SlowIdSegmentDistributor();
        SegmentChainId generator = new SegmentChainId(TIME_TO_LIVE_FOREVER, 2, 2, distributor, new NoopPrefetchWorkerExecutorService(),
            CallTimeout.of(Duration.ofMillis(50)));

        distributor.slow = true;
        long start = System.nanoTime();
        generator.getPrefetchJob().prefetch();
        generator.getPrefetchJob().prefetch();
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0);

        distributor.slow = false;
        // The given-up call may still take its segments from the distributor: those ids are skipped, never reused.
        assertTrue(generator.generate() > 0);
    }

    private static final class SlowIdSegmentDistributor extends AtomicIdSegmentDistributor {
        private volatile boolean slow;

        private SlowIdSegmentDistributor() {
            super(2);
        }

        @Override
        public long nextMaxId(long step) {
            if (slow) {
                try {
                    Thread.sleep(Duration.ofSeconds(30).toMillis());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.nextMaxId(step);
        }
    }

    private static final class NoopPrefetchWorkerExecutorService extends PrefetchWorkerExecutorService {
        private final NoopPrefetchWorker worker = new NoopPrefetchWorker();
        private final AtomicInteger submitted = new AtomicInteger();

        private NoopPrefetchWorkerExecutorService() {
            super(Duration.ofDays(1), 1, false);
        }

        @Override
        public void submit(AffinityJob affinityJob) {
            submitted.incrementAndGet();
            affinityJob.setPrefetchWorker(worker);
        }
    }

    private static final class NoopPrefetchWorker implements PrefetchWorker {
        private final AtomicInteger cancelled = new AtomicInteger();

        @Override
        public String getName() {
            return "noop-prefetch-worker";
        }

        @Override
        public void submit(AffinityJob affinityJob) {
        }

        @Override
        public void cancel(AffinityJob affinityJob) {
            cancelled.incrementAndGet();
        }

        @Override
        public void wakeup(AffinityJob affinityJob) {
        }

        @Override
        public void shutdown() {
        }
    }
}
