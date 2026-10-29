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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.RangeUnavailableException;
import me.ahoo.cosid.internal.RangeCursor;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class SegmentGeneratorTest {
    private static final String KEY = "app/order";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final FakeRangeStore store = new FakeRangeStore();
    private final RecordingListener listener = new RecordingListener();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static BatchPolicy policy(long step, Duration lookahead, long maxBatch, Duration reserveTimeout, Optional<Duration> rangeTtl) {
        BatchPolicy.Builder policy = BatchPolicy.builder().step(step).initialOffset(0).lookahead(lookahead).maxBatch(maxBatch).reserveTimeout(reserveTimeout);
        rangeTtl.ifPresent(policy::rangeTtl);
        return policy.build();
    }

    private static BatchPolicy noPrefetch(long step) {
        return policy(step, Duration.ZERO, step, Duration.ofSeconds(3), Optional.empty());
    }

    private SegmentGenerator generator(BatchPolicy policy, java.util.concurrent.Executor refill, TimeSource time) {
        return new SegmentGenerator("order", KEY, store, policy, refill, time, listener);
    }

    @Test
    void issuesFromInitialOffsetInOrder() {
        BatchPolicy policy = BatchPolicy.builder()
            .step(10)
            .initialOffset(1000)
            .lookahead(Duration.ZERO)
            .maxBatch(10)
            .reserveTimeout(Duration.ofSeconds(3))
            .build();
        try (SegmentGenerator generator = generator(policy, Runnable::run, TimeSource.SYSTEM)) {
            assertThat(store.reserves.get(), is(0));
            for (long expected = 1000; expected < 1025; expected++) {
                assertThat(generator.generate(), is(expected));
            }
            assertThat(store.reserved(), contains(new Range(1000, 1010), new Range(1010, 1020), new Range(1020, 1030)));
            assertThat(listener.reserved.size(), is(3));
        }
    }

    @Test
    void defaultsNeverIssueZero() {
        try (SegmentGenerator generator = generator(BatchPolicy.DEFAULT, Runnable::run, TimeSource.SYSTEM)) {
            assertThat(generator.generate(), is(1L));
            assertThat(generator.generate(), is(2L));
            assertThat(store.reserved().get(0).start(), is(1L));
        }
    }

    @Test
    void anExplicitZeroOffsetStartsAtZero() {
        BatchPolicy fromZero = BatchPolicy.builder()
            .step(10)
            .initialOffset(0)
            .lookahead(Duration.ZERO)
            .maxBatch(10)
            .reserveTimeout(Duration.ofSeconds(3))
            .build();
        try (SegmentGenerator generator = generator(fromZero, Runnable::run, TimeSource.SYSTEM)) {
            assertThat(generator.generate(), is(0L));
        }
    }

    @Test
    void withoutLookaheadReservesOnlyWhenUsedUp() {
        try (SegmentGenerator generator = generator(noPrefetch(1), Runnable::run, TimeSource.SYSTEM)) {
            for (long expected = 0; expected < 50; expected++) {
                assertThat(generator.generate(), is(expected));
                assertThat(generator.held(), is(0L));
            }
            assertThat(store.reserves.get(), is(50));
        }
    }

    @Test
    void stepOneWithoutLookaheadTakesEachValueFromItsReservationWithoutTheExecutorOrTheClock() {
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger clockReads = new AtomicInteger();
        TimeSource time = () -> {
            clockReads.incrementAndGet();
            return 0;
        };
        // R1-core-04 (P6): the caller makes the reservation itself and takes its one value; nothing is queued.
        try (SegmentGenerator generator = generator(noPrefetch(1), command -> submitted.incrementAndGet(), time)) {
            for (long expected = 0; expected < 100; expected++) {
                assertThat(generator.generate(), is(expected));
            }
            assertThat(store.reserves.get(), is(100));
            assertThat(submitted.get(), is(0));
            assertThat(clockReads.get(), is(0));
            assertThat(listener.reserved.size(), is(100));
        }
    }

    @Test
    void aCallerThatReservesTakesTheFirstValueAndQueuesTheRest() {
        AtomicInteger submitted = new AtomicInteger();
        try (SegmentGenerator generator = generator(noPrefetch(10), command -> submitted.incrementAndGet(), TimeSource.SYSTEM)) {
            assertThat(generator.generate(), is(0L));
            assertThat(generator.held(), is(9L));
            for (long expected = 1; expected < 25; expected++) {
                assertThat(generator.generate(), is(expected));
            }
            assertThat(store.reserved(), contains(new Range(0, 10), new Range(10, 20), new Range(20, 30)));
            assertThat(generator.held(), is(5L));
            assertThat(submitted.get(), is(0));
        }
    }

    @Test
    void theValueACallerTakesIsCheckedLikeTheRangeAndWithinItsTtl() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(1, Duration.ZERO, 1, Duration.ofSeconds(3), Optional.of(Duration.ofSeconds(1)));
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            assertThat(generator.generate(), is(0L));
            time.advance(5_000);
            assertThat(generator.generate(), is(1L));
            store.override = new Range(1, 2);
            assertThrows(RangeUnavailableException.class, generator::generate);
        }
    }

    @Test
    void neverReservesUnderTheSwitchLock() {
        AtomicReference<SegmentGenerator> self = new AtomicReference<>();
        AtomicBoolean underLock = new AtomicBoolean();
        store.onReserve = key -> underLock.compareAndSet(false, self.get().switchLockHeldByCurrentThread());
        // A direct executor runs the reservation on the generating thread: it must have released the lock by then.
        try (SegmentGenerator generator = generator(policy(10, Duration.ofSeconds(1), 1000, Duration.ofSeconds(3), Optional.empty()),
            Runnable::run, TimeSource.SYSTEM)) {
            self.set(generator);
            for (int i = 0; i < 10_000; i++) {
                generator.generate();
            }
        }
        assertThat(store.reserves.get(), greaterThan(1));
        assertThat(underLock.get(), is(false));
    }

    @Test
    void blockedStoreDoesNotStopOtherThreadsIssuingReadyRanges() throws Exception {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(100, Duration.ofSeconds(1), 100, Duration.ofSeconds(5), Optional.empty());
        try (SegmentGenerator generator = generator(policy, executor, time)) {
            assertThat(generator.generate(), is(0L));
            awaitHeld(generator, 199);
            // The current range (99 left) and one ready range (100): the refill of the next one blocks in the store.
            CountDownLatch gate = new CountDownLatch(1);
            store.gate = gate;
            int threads = 4;
            int perThread = 199 / threads;
            List<Future<List<Long>>> results = new ArrayList<>();
            CyclicBarrier start = new CyclicBarrier(threads);
            for (int t = 0; t < threads; t++) {
                results.add(executor.submit(() -> {
                    start.await();
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.generate());
                    }
                    return ids;
                }));
            }
            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (Future<List<Long>> result : results) {
                ids.addAll(result.get(5, TimeUnit.SECONDS));
            }
            assertThat(ids.size(), is(threads * perThread));
            assertThat(ids, everyItem(lessThanOrEqualTo(199L)));
            assertThat("the refill is blocked in the store", gate.getCount(), is(1L));
            gate.countDown();
        }
    }

    @Test
    void waitsForTheRefillOutsideTheLockAndTimesOut() {
        CountDownLatch gate = new CountDownLatch(1);
        store.gate = gate;
        BatchPolicy policy = policy(10, Duration.ZERO, 10, Duration.ofMillis(100), Optional.empty());
        try (SegmentGenerator generator = generator(policy, executor, TimeSource.SYSTEM)) {
            // The executor makes the reservation (blocked in the store); the caller only waits for it.
            generator.prefetch();
            awaitReserves(1);
            RangeUnavailableException e = assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(e.key(), is(KEY));
            assertThat(e.getCause(), instanceOf(TimeoutException.class));
            // Waiting again joins the same reservation: single flight.
            assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(store.reserves.get(), is(1));
            gate.countDown();
            awaitHeld(generator, 10);
            // The timed-out reservation still delivers its range: no gap.
            assertThat(generator.generate(), is(0L));
            assertThat(store.reserves.get(), is(1));
        }
    }

    @Test
    void callerMakesAReservationTheExecutorHasNotStarted() {
        List<Runnable> queued = new ArrayList<>();
        try (SegmentGenerator generator = generator(policy(10, Duration.ofSeconds(1), 10, Duration.ofSeconds(3), Optional.empty()),
            queued::add, TimeSource.SYSTEM)) {
            generator.prefetch();
            assertThat(queued.size(), is(1));
            assertThat(generator.generate(), is(0L));
            assertThat(store.reserves.get(), is(1));
            // The executor finds it claimed and does nothing.
            queued.get(0).run();
            assertThat(store.reserves.get(), is(1));
        }
    }

    @Test
    void concurrentWaitersShareOneReservation() throws Exception {
        store.latencyMillis = 50;
        BatchPolicy policy = policy(1000, Duration.ZERO, 1000, Duration.ofSeconds(5), Optional.empty());
        try (SegmentGenerator generator = generator(policy, executor, TimeSource.SYSTEM)) {
            int threads = 16;
            CyclicBarrier start = new CyclicBarrier(threads);
            List<Future<Long>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return generator.generate();
                }));
            }
            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (Future<Long> result : results) {
                ids.add(result.get(5, TimeUnit.SECONDS));
            }
            assertThat(ids.size(), is(threads));
            assertThat(store.reserves.get(), is(1));
        }
    }

    @Test
    void failedRefillBacksOffBeforeRetrying() {
        ManualTime time = new ManualTime(0);
        IllegalStateException down = new IllegalStateException("store down");
        store.failure = down;
        try (SegmentGenerator generator = generator(noPrefetch(10), Runnable::run, time)) {
            RangeUnavailableException first = assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(first.getCause(), sameInstance(down));
            assertThat(listener.failures, contains(down));
            assertThat(store.reserves.get(), is(1));
            // Within the backoff no reservation starts: the caller fails at once with the last failure.
            RangeUnavailableException backingOff = assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(backingOff.getCause(), sameInstance(down));
            assertThat(store.reserves.get(), is(1));

            long[] backoffs = {100, 200, 400, 800, 1600, 3200, 5000, 5000};
            for (int i = 0; i < backoffs.length; i++) {
                time.advance(backoffs[i] - 1);
                assertThrows(RangeUnavailableException.class, generator::generate);
                assertThat(store.reserves.get(), is(i + 1));
                time.advance(1);
                assertThrows(RangeUnavailableException.class, generator::generate);
                assertThat(store.reserves.get(), is(i + 2));
            }
            store.failure = null;
            time.advance(5000);
            assertThat(generator.generate(), is(0L));
            // A success resets the backoff.
            store.failure = down;
            for (int i = 1; i < 10; i++) {
                generator.generate();
            }
            assertThrows(RangeUnavailableException.class, generator::generate);
            time.advance(100);
            store.failure = null;
            assertThat(generator.generate(), is(10L));
        }
    }

    @Test
    void lostResponsesLeaveGapsNeverDuplicates() {
        ManualTime time = new ManualTime(0);
        store.loseResponses = true;
        try (SegmentGenerator generator = generator(noPrefetch(10), Runnable::run, time)) {
            assertThrows(RangeUnavailableException.class, generator::generate);
            store.loseResponses = false;
            time.advance(100);
            assertThat(generator.generate(), is(10L));
        }
    }

    @Test
    void sealedKeyIsRefused() {
        ManualTime time = new ManualTime(0);
        store.sealNow(KEY);
        try (SegmentGenerator generator = generator(noPrefetch(10), Runnable::run, time)) {
            RangeSealedException sealed = assertThrows(RangeSealedException.class, generator::generate);
            assertThat(sealed.key(), is(KEY));
            assertThrows(RangeSealedException.class, generator::generate);
            time.advance(100);
            assertThrows(RangeSealedException.class, generator::generate);
            assertThat(store.exists(KEY), is(false));
        }
    }

    @Test
    void exhaustedKeyPropagates() {
        store.limit = 15;
        try (SegmentGenerator generator = generator(noPrefetch(10), Runnable::run, new ManualTime(0))) {
            for (long expected = 0; expected < 10; expected++) {
                assertThat(generator.generate(), is(expected));
            }
            assertThrows(RangeExhaustedException.class, generator::generate);
            assertThrows(RangeExhaustedException.class, generator::generate);
        }
    }

    @Test
    void rangeEndingNearLongMaxIsCappedNotWrapped() {
        store.override = new Range(Long.MAX_VALUE - 10, Long.MAX_VALUE);
        store.limit = Long.MAX_VALUE;
        try (SegmentGenerator generator = generator(noPrefetch(10), Runnable::run, new ManualTime(0))) {
            // Nothing of the range is below the cap: it is dropped, and the next reservation is above it (exhausted).
            store.limit = 0;
            assertThrows(RangeExhaustedException.class, generator::generate);
        }
        assertThat(RangeCursor.of(Long.MAX_VALUE - 10, Long.MAX_VALUE), is((RangeCursor) null));
        RangeCursor cursor = RangeCursor.of(0, Long.MAX_VALUE);
        assertThat(cursor.end(), is(Long.MAX_VALUE - RangeCursor.HEADROOM));
    }

    @Test
    void overlappingRangeFromStoreIsRefused() {
        ManualTime time = new ManualTime(0);
        try (SegmentGenerator generator = generator(noPrefetch(10), Runnable::run, time)) {
            for (int i = 0; i < 10; i++) {
                generator.generate();
            }
            store.override = new Range(5, 15);
            RangeUnavailableException e = assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(e.getCause(), instanceOf(IllegalStateException.class));
            time.advance(100);
            assertThat(generator.generate(), is(10L));
        }
    }

    @Test
    void rejectedRefillFailsTheCaller() {
        java.util.concurrent.Executor rejecting = command -> {
            throw new RejectedExecutionException("shut down");
        };
        ManualTime time = new ManualTime(0);
        try (SegmentGenerator generator = generator(noPrefetch(10), rejecting, time)) {
            // A prefetch the executor refuses fails like a failed reservation and backs off...
            generator.prefetch();
            assertThat(listener.failures.get(0), instanceOf(RejectedExecutionException.class));
            RangeUnavailableException e = assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(e.getCause(), instanceOf(RejectedExecutionException.class));
            // ...but a caller with nothing at hand makes the reservation itself.
            time.advance(100);
            assertThat(generator.generate(), is(0L));
        }
    }

    @Test
    void failingListenerDoesNotBreakGeneration() {
        GeneratorListener failing = new GeneratorListener() {
            @Override
            public void onRangeReserved(String generator, String key, Range range, Duration latency) {
                throw new IllegalStateException("listener");
            }
        };
        try (SegmentGenerator generator = new SegmentGenerator("order", KEY, store, noPrefetch(10), Runnable::run, TimeSource.SYSTEM, failing)) {
            assertThat(generator.generate(), is(0L));
        }
    }

    @Test
    void interruptedWaiterFails() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        store.gate = gate;
        try (SegmentGenerator generator = generator(noPrefetch(10), executor, TimeSource.SYSTEM)) {
            generator.prefetch();
            awaitReserves(1);
            CompletableFuture<Throwable> failure = new CompletableFuture<>();
            Thread waiter = Thread.ofPlatform().start(() -> {
                try {
                    generator.generate();
                    failure.complete(null);
                } catch (RuntimeException e) {
                    failure.complete(Thread.currentThread().isInterrupted() ? e : null);
                }
            });
            Thread.sleep(50);
            waiter.interrupt();
            Throwable e = failure.get(5, TimeUnit.SECONDS);
            assertThat(e, instanceOf(RangeUnavailableException.class));
            assertThat(e.getCause(), instanceOf(InterruptedException.class));
            gate.countDown();
        }
    }

    @Test
    void closeStopsIssuingAndDropsReadyRanges() {
        SegmentGenerator generator = generator(policy(10, Duration.ofSeconds(1), 10, Duration.ofSeconds(3), Optional.empty()),
            Runnable::run, new ManualTime(0));
        assertThat(generator.generate(), is(0L));
        assertThat(generator.held(), is(19L));
        generator.close();
        assertThat(generator.held(), is(0L));
        IllegalStateException e = assertThrows(IllegalStateException.class, generator::generate);
        assertThat(e.getMessage(), is("Segment generator [order] is closed"));
        generator.close();
        assertThrows(IllegalStateException.class, generator::generate);
        int reserves = store.reserves.get();
        generator.prefetch();
        assertThat(store.reserves.get(), is(reserves));
    }

    @Test
    void closeReleasesWaitersAndDropsTheLateRange() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        store.gate = gate;
        SegmentGenerator generator = generator(noPrefetch(10), executor, TimeSource.SYSTEM);
        generator.prefetch();
        awaitReserves(1);
        Future<?> waiter = executor.submit(generator::generate);
        Thread.sleep(50);
        generator.close();
        java.util.concurrent.ExecutionException e = assertThrows(java.util.concurrent.ExecutionException.class, () -> waiter.get(5, TimeUnit.SECONDS));
        assertThat(e.getCause(), instanceOf(IllegalStateException.class));
        gate.countDown();
        assertTimeoutPreemptively(TIMEOUT, () -> {
            while (store.reserved().isEmpty()) {
                Thread.onSpinWait();
            }
        });
        Thread.sleep(50);
        assertThat(generator.held(), is(0L));
        assertThat(listener.reserved, empty());
    }

    @Test
    void closedBeforeTheRefillRunsSkipsTheStore() {
        List<Runnable> queued = new ArrayList<>();
        SegmentGenerator generator = generator(policy(10, Duration.ofSeconds(1), 10, Duration.ofSeconds(3), Optional.empty()),
            queued::add, TimeSource.SYSTEM);
        generator.prefetch();
        assertThat(queued.size(), is(1));
        generator.close();
        queued.get(0).run();
        assertThat(store.reserves.get(), is(0));
    }

    @Test
    void rangeTtlDropsWhatOutlivesIt() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(10, Duration.ZERO, 10, Duration.ofSeconds(3), Optional.of(Duration.ofSeconds(1)));
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            assertThat(generator.generate(), is(0L));
            assertThat(generator.generate(), is(1L));
            time.advance(999);
            assertThat(generator.generate(), is(2L));
            time.advance(1);
            // The rest of [0, 10) expired: the next value comes from a new range.
            assertThat(generator.generate(), is(10L));
            assertThat(generator.held(), is(9L));
            time.advance(1000);
            assertThat(generator.held(), is(0L));
            assertThat(generator.generate(), is(20L));
        }
    }

    @Test
    void rangeTtlAlsoDropsExpiredReadyRanges() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(10, Duration.ofSeconds(1), 10, Duration.ofSeconds(3), Optional.of(Duration.ofSeconds(1)));
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            assertThat(generator.generate(), is(0L));
            assertThat(generator.held(), is(19L));
            time.advance(1000);
            assertThat(generator.generate(), is(20L));
        }
    }

    @Test
    void batchFollowsTheRateWithinStepAndMaxBatch() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(100, Duration.ofSeconds(1), 10_000, Duration.ofSeconds(3), Optional.empty());
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            assertThat(generator.batch(), is(100L));
            // 100 values per 10ms = 10 per ms: a 1s budget is 10_000, the cap.
            for (int i = 0; i < 2000; i++) {
                generator.generate();
                if (i % 10 == 0) {
                    time.advance(1);
                }
            }
            assertThat(generator.batch(), is(10_000L));
            // 1 value per 10 ms = 0.1 per ms: a 1s budget is 100, the floor.
            for (int i = 0; i < 40_000; i++) {
                generator.generate();
                time.advance(10);
            }
            assertThat(generator.batch(), is(100L));
            assertThat(listener.resized.size(), greaterThanOrEqualTo(2));
            for (Range range : store.reserved()) {
                long size = range.end() - range.start();
                assertThat(size % 100, is(0L));
                assertThat(size, lessThanOrEqualTo(10_000L));
            }
            // The values at hand stay bounded: the current range and less than two batches ready.
            assertThat(generator.held(), lessThanOrEqualTo(3 * 10_000L));
        }
    }

    @Test
    void everyResizeIsReportedWithAStepOfOne() {
        // R2-core-01: with step 1 and a lookahead a caller may take the only value of a range itself; the sample of that
        // value, taken when the next range arrives, may resize the batch, which onBatchResized reports as any other.
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(1, Duration.ofSeconds(1), 1000, Duration.ofSeconds(3), Optional.empty());
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            long batch = generator.batch();
            for (int i = 0; i < 3000; i++) {
                generator.generate();
                if (i % 3 == 0) {
                    time.advance(1);
                }
                if (generator.batch() != batch) {
                    batch = generator.batch();
                    long reported = listener.resized.isEmpty() ? -1 : listener.resized.get(listener.resized.size() - 1)[1];
                    assertThat("the resize to " + batch + " after " + i, reported, is(batch));
                }
            }
            assertThat(listener.resized.isEmpty(), is(false));
        }
    }

    @Test
    void largeDefaultMaxBatchKeepsTheRestartSmall() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(10, Duration.ofSeconds(1), BatchPolicy.defaultMaxBatch(10), Duration.ofSeconds(3), Optional.empty());
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            // The first reservation is one step: nothing is known of the rate yet.
            assertThat(generator.generate(), is(0L));
            assertThat(store.reserved().get(0), is(new Range(0, 10)));
            // 1 value per ms: the batch follows the 1s budget (1000), far below the cap.
            for (int i = 1; i < 5000; i++) {
                time.advance(1);
                generator.generate();
            }
            assertThat(generator.batch(), lessThanOrEqualTo(2000L));
            long reserved = store.reserved().stream().mapToLong(range -> range.end() - range.start()).sum();
            assertThat(reserved, lessThanOrEqualTo(5000L + 3 * 2000L));
        }
    }

    @Test
    void fastRateGrowsTheBatchPastTheOldStepTimesThousandCap() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(10, Duration.ofSeconds(1), BatchPolicy.defaultMaxBatch(10), Duration.ofSeconds(3), Optional.empty());
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            // 100 values per ms: a 1s budget of 100_000, ten times the old cap of step * 1000.
            for (int i = 0; i < 1_000_000; i++) {
                generator.generate();
                if (i % 100 == 0) {
                    time.advance(1);
                }
            }
            assertThat(generator.batch(), greaterThan(10_000L));
            assertThat(generator.batch(), lessThanOrEqualTo(BatchPolicy.defaultMaxBatch(10)));
        }
    }

    @Test
    void aBatchNearLongMaxStillRefills() {
        // batch * MAX_READY_RANGES would wrap negative: the refill must still start, not busy-loop.
        long step = Long.MAX_VALUE / 32;
        BatchPolicy huge = BatchPolicy.builder()
            .step(step)
            .initialOffset(1)
            .lookahead(Duration.ofSeconds(1))
            .maxBatch(step)
            .reserveTimeout(Duration.ofSeconds(3))
            .build();
        try (SegmentGenerator generator = generator(huge, Runnable::run, new ManualTime(0))) {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                assertThat(generator.generate(), is(1L));
                assertThat(generator.generate(), is(2L));
            });
        }
    }

    @Test
    void batchRoundsUpToTheStep() {
        ManualTime time = new ManualTime(0);
        BatchPolicy policy = policy(100, Duration.ofMillis(150), 100_000, Duration.ofSeconds(3), Optional.empty());
        try (SegmentGenerator generator = generator(policy, Runnable::run, time)) {
            // 100 values per 10ms = 10 per ms; 150ms is 1500, a multiple of 100 once rounded up.
            for (int i = 0; i < 5000; i++) {
                generator.generate();
                if (i % 10 == 0) {
                    time.advance(1);
                }
            }
            assertThat(generator.batch(), is(1500L));
        }
    }

    /**
     * The 4.x #1299 scenario: threads that find no value at once must not skip a range another thread brought in; with
     * no failure, no expiry and no close, every value of every range is issued, in order across ranges.
     */
    @Test
    void concurrentGenerationIssuesEveryValueOfEveryRange() throws Exception {
        int threads = 16;
        int perThread = 500;
        for (int round = 0; round < 200; round++) {
            FakeRangeStore fresh = new FakeRangeStore();
            BatchPolicy policy = policy(10, Duration.ofSeconds(1), 1000, Duration.ofSeconds(10), Optional.empty());
            try (SegmentGenerator generator = new SegmentGenerator("gap", "app/gap" + round, fresh, policy, executor, TimeSource.SYSTEM,
                GeneratorListener.NOOP)) {
                long[] ids = generateConcurrently(generator, threads, perThread);
                java.util.Arrays.sort(ids);
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i] != i) {
                        throw new AssertionError("round " + round + ": expected " + i + " at " + i + " but was " + ids[i]);
                    }
                }
            }
        }
    }

    @Test
    void stressUniqueAcrossManySwitchesWithSlowStore() throws Exception {
        store.latencyMillis = 1;
        BatchPolicy policy = policy(10, Duration.ofMillis(5), 100, Duration.ofSeconds(10), Optional.empty());
        try (SegmentGenerator generator = generator(policy, executor, TimeSource.SYSTEM)) {
            long[] ids = generateConcurrently(generator, 32, 2000);
            java.util.Arrays.sort(ids);
            for (int i = 1; i < ids.length; i++) {
                assertThat(ids[i], not(ids[i - 1]));
            }
            assertThat(store.reserved().size(), greaterThan(10));
        }
    }

    private long[] generateConcurrently(SegmentGenerator generator, int threads, int perThread) throws Exception {
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Future<long[]>> results = new ArrayList<>();
        try (ExecutorService callers = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                results.add(callers.submit(() -> {
                    start.await();
                    long[] ids = new long[perThread];
                    for (int i = 0; i < perThread; i++) {
                        ids[i] = generator.generate();
                    }
                    return ids;
                }));
            }
            long[] all = new long[threads * perThread];
            for (int t = 0; t < threads; t++) {
                System.arraycopy(results.get(t).get(30, TimeUnit.SECONDS), 0, all, t * perThread, perThread);
            }
            return all;
        }
    }

    private void awaitHeld(SegmentGenerator generator, long held) {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            while (generator.held() < held) {
                Thread.sleep(1);
            }
        });
    }

    private void awaitReserves(int reserves) {
        AtomicInteger count = store.reserves;
        assertTimeoutPreemptively(TIMEOUT, () -> {
            while (count.get() < reserves) {
                Thread.sleep(1);
            }
        });
    }

    private static final class AtomicReference<T> extends java.util.concurrent.atomic.AtomicReference<T> {
    }
}
