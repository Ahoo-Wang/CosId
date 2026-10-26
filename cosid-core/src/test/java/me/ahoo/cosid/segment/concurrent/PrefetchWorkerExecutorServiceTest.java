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

package me.ahoo.cosid.segment.concurrent;

import me.ahoo.cosid.test.ShutdownHooks;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PrefetchWorkerExecutorServiceTest .
 *
 * @author ahoo wang
 */
class PrefetchWorkerExecutorServiceTest {
    PrefetchWorkerExecutorService executorService;
    private final AtomicInteger jobId = new AtomicInteger();
    
    @BeforeEach
    void setup() {
        executorService = new PrefetchWorkerExecutorService(Duration.ofMillis(10), 2, false);
    }
    
    @AfterEach
    void destroy() {
        if (Objects.nonNull(executorService)) {
            executorService.shutdown();
        }
    }
    
    @Test
    void shutdown() {
        executorService.shutdown();
        // Multiple shutdowns have no side effects
        executorService.shutdown();
    }
    
    @Test
    void shutdownRemovesItsShutdownHook() {
        int hooksBefore = ShutdownHooks.count();
        PrefetchWorkerExecutorService withHook = new PrefetchWorkerExecutorService(Duration.ofMillis(10), 1, true);
        Assertions.assertEquals(hooksBefore + 1, ShutdownHooks.count());

        withHook.shutdown();

        Assertions.assertEquals(hooksBefore, ShutdownHooks.count());
    }

    @SneakyThrows
    @Test
    void shutdownStopsAParkedWorker() {
        PrefetchWorkerExecutorService slow = new PrefetchWorkerExecutorService(Duration.ofMinutes(1), 1, false);
        TestAffinityJob affinityJob = new TestAffinityJob(nextJobId(), 1);
        slow.submit(affinityJob);
        Assertions.assertTrue(affinityJob.awaitRuns(2, TimeUnit.SECONDS), affinityJob::diagnostics);
        DefaultPrefetchWorker worker = (DefaultPrefetchWorker) affinityJob.getPrefetchWorker();

        slow.shutdown();

        worker.join(TimeUnit.SECONDS.toMillis(2));
        Assertions.assertFalse(worker.isAlive(), () -> "worker state: " + worker.getState());
    }

    /**
     * The executor starts a worker before handing it the job: a worker already parked for its prefetch period runs a
     * job submitted meanwhile now, not a period later.
     */
    @SneakyThrows
    @Test
    void aParkedWorkerRunsASubmittedJobNow() {
        DefaultPrefetchWorker worker = new DefaultPrefetchWorker(Duration.ofMinutes(1));
        worker.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            Assertions.assertEquals(Thread.State.TIMED_WAITING, worker.getState());
            TestAffinityJob affinityJob = new TestAffinityJob(nextJobId(), 1);

            worker.submit(affinityJob);

            Assertions.assertTrue(affinityJob.awaitRuns(2, TimeUnit.SECONDS), affinityJob::diagnostics);
        } finally {
            worker.shutdown();
            worker.join(TimeUnit.SECONDS.toMillis(2));
        }
    }

    @Test
    void submit() {
        TestAffinityJob affinityJob = new TestAffinityJob(nextJobId(), 1);

        executorService.submit(affinityJob);

        Assertions.assertInstanceOf(PrefetchWorker.class, affinityJob.getPrefetchWorker());
        Assertions.assertTrue(affinityJob.awaitRuns(2, TimeUnit.SECONDS), affinityJob::diagnostics);
    }
    
    @Test
    void submitWhenShutdown() {
        executorService.shutdown();
        TestAffinityJob affinityJob = new TestAffinityJob(nextJobId(), 1);
        
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            executorService.submit(affinityJob);
        });
        Assertions.assertEquals("PrefetchWorkerExecutorService is shutdown.", exception.getMessage());
        Assertions.assertNull(affinityJob.getPrefetchWorker());
    }

    @Test
    void submitWhenAlreadyBoundDoesNotReassignWorker() {
        TestAffinityJob affinityJob = new TestAffinityJob(nextJobId(), 1);
        PrefetchWorker boundWorker = new DefaultPrefetchWorker(Duration.ofMillis(10));
        affinityJob.setPrefetchWorker(boundWorker);

        executorService.submit(affinityJob);

        Assertions.assertSame(boundWorker, affinityJob.getPrefetchWorker());
        Assertions.assertFalse(affinityJob.awaitRuns(100, TimeUnit.MILLISECONDS), affinityJob::diagnostics);
    }
    
    @SneakyThrows
    @Test
    void checkBoundThread() {
        TestAffinityJob affinityJob = new TestAffinityJob(nextJobId(), 5);

        executorService.submit(affinityJob);

        Assertions.assertTrue(affinityJob.awaitRuns(2, TimeUnit.SECONDS), affinityJob::diagnostics);
        Assertions.assertEquals(1, affinityJob.runThreads.get(), affinityJob::diagnostics);
    }

    private String nextJobId() {
        return "job-" + jobId.incrementAndGet();
    }

    private static final class TestAffinityJob implements AffinityJob {
        private final String jobId;
        private final CountDownLatch runs;
        private final AtomicReference<Thread> boundThread = new AtomicReference<>();
        private final AtomicInteger runThreads = new AtomicInteger();
        private final AtomicInteger runCount = new AtomicInteger();
        private volatile PrefetchWorker prefetchWorker;
        private volatile long hungerTime;

        private TestAffinityJob(String jobId, int expectedRuns) {
            this.jobId = jobId;
            this.runs = new CountDownLatch(expectedRuns);
        }

        @Override
        public String getJobId() {
            return jobId;
        }

        @Override
        public void setHungerTime(long hungerTime) {
            this.hungerTime = hungerTime;
        }

        @Override
        public PrefetchWorker getPrefetchWorker() {
            return prefetchWorker;
        }

        @Override
        public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            this.prefetchWorker = prefetchWorker;
        }

        @Override
        public void run() {
            runCount.incrementAndGet();
            if (boundThread.compareAndSet(null, Thread.currentThread())) {
                runThreads.incrementAndGet();
            } else if (!Thread.currentThread().equals(boundThread.get())) {
                runThreads.incrementAndGet();
            }
            runs.countDown();
        }

        @SneakyThrows
        private boolean awaitRuns(long timeout, TimeUnit unit) {
            return runs.await(timeout, unit);
        }

        private String diagnostics() {
            return "jobId=" + jobId
                + ", runCount=" + runCount.get()
                + ", expectedRemainingRuns=" + runs.getCount()
                + ", worker=" + prefetchWorker
                + ", boundThread=" + boundThread.get()
                + ", observedRunThreads=" + runThreads.get()
                + ", hungerTime=" + hungerTime;
        }
    }
    
}
