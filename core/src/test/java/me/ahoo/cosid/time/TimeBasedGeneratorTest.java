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

package me.ahoo.cosid.time;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.ClockBackwardsException;
import me.ahoo.cosid.SlotLostException;
import me.ahoo.cosid.SlotWindowExceededException;
import me.ahoo.cosid.observe.GeneratorListener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

@Timeout(60)
class TimeBasedGeneratorTest {
    private static final long EPOCH = Layout.COSID_EPOCH.toEpochMilli();
    private static final long T = 1_000_000;
    private static final long T0 = EPOCH + T;
    /** Three sequence bits, reset threshold 3: small enough to exhaust and to see the scattering. */
    private static final Layout TINY = new Layout(Layout.COSID_EPOCH, ChronoUnit.MILLIS, 41, 10, 3, 3);
    private static final ClockRegressionPolicy WAIT_200MS = new ClockRegressionPolicy(Duration.ZERO, Duration.ofMillis(200));

    private final ManualTimeSource time = new ManualTimeSource(T0);
    private final RecordingListener listener = new RecordingListener();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private TimeBasedGenerator generator(Layout layout, FakeSlot slot, ClockRegressionPolicy regression) {
        return new TimeBasedGenerator("test", layout, slot, time, regression, listener);
    }

    private TimeBasedGenerator generator(Layout layout) {
        return generator(layout, FakeSlot.forever(5), ClockRegressionPolicy.DEFAULT);
    }

    private <T> Future<T> blocked(java.util.concurrent.Callable<T> call) throws InterruptedException {
        Future<T> future = executor.submit(call);
        Thread.sleep(50);
        assertThat("still waiting", future.isDone(), is(false));
        return future;
    }

    private static <T> T done(Future<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void issuesTimeSlotAndSequence() {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS);
        assertThat(generator.lastIssuedMillis(), is(0L));
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()), is(new IdParts(T, 5, 0)));
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()), is(new IdParts(T, 5, 1)));
        assertThat(generator.slot(), is(5));
        assertThat(generator.lastIssuedMillis(), is(T0));
        time.advance(1);
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()), is(new IdParts(T + 1, 5, 2)));
    }

    @Test
    void newTimeUnitKeepsTheSequenceBelowTheResetThreshold() {
        TimeBasedGenerator generator = generator(TINY);
        List<Long> seqs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            seqs.add(TINY.unpack(generator.generate()).seq());
            time.advance(1);
        }
        assertThat(seqs, contains(0L, 1L, 2L, 3L, 0L, 1L));
    }

    @Test
    void exhaustedSequenceWaitsForTheNextUnit() throws Exception {
        TimeBasedGenerator generator = generator(TINY);
        for (int seq = 0; seq <= 7; seq++) {
            assertThat(TINY.unpack(generator.generate()), is(new IdParts(T, 5, seq)));
        }
        Future<Long> next = blocked(generator::generate);
        assertThat(listener.events, hasItem("exhausted"));
        time.advance(1);
        assertThat(TINY.unpack(done(next)), is(new IdParts(T + 1, 5, 0)));
    }

    @Test
    void smallRegressionBorrowsTheLastUnit() {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS);
        generator.generate();
        time.set(T0 - 3);
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()), is(new IdParts(T, 5, 1)));
        assertThat(listener.events, contains("borrow:3"));
    }

    @Test
    void borrowingAnExhaustedUnitTakesTheNextOne() {
        TimeBasedGenerator generator = generator(TINY);
        for (int seq = 0; seq <= 7; seq++) {
            generator.generate();
        }
        time.set(T0 - 2);
        assertThat(TINY.unpack(generator.generate()), is(new IdParts(T + 1, 5, 0)));
        assertThat(listener.events, contains("borrow:2"));
    }

    @Test
    void mediumRegressionWaits() throws Exception {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS);
        generator.generate();
        time.set(T0 - 100);
        Future<Long> next = blocked(generator::generate);
        assertThat(listener.events, hasItem("wait:100"));
        time.set(T0);
        assertThat(Layout.SNOWFLAKE_MS.unpack(done(next)), is(new IdParts(T, 5, 1)));
    }

    @Test
    void largeRegressionFails() {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS);
        generator.generate();
        time.set(T0 - 2000);
        ClockBackwardsException error = assertThrows(ClockBackwardsException.class, generator::generate);
        assertThat(error.backwardsMillis(), is(2000L));
        assertThat(error.generator(), is("test"));
        assertThat(listener.events, contains("fail:2000"));
        time.set(T0);
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()), is(new IdParts(T, 5, 1)));
    }

    @Test
    void failPolicyFailsOnAnyRegression() {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, FakeSlot.forever(5), ClockRegressionPolicy.FAIL);
        generator.generate();
        time.set(T0 - 1);
        assertThrows(ClockBackwardsException.class, generator::generate);
    }

    @Test
    void waitsUntilAboveTheFloor() throws Exception {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, new FakeSlot(1, T0 + 50, Long.MAX_VALUE), ClockRegressionPolicy.FAIL);
        Future<Long> first = blocked(generator::generate);
        time.set(T0 + 50);
        Thread.sleep(30);
        assertThat("the floor itself is not above the floor", first.isDone(), is(false));
        time.set(T0 + 51);
        assertThat(Layout.SNOWFLAKE_MS.unpack(done(first)), is(new IdParts(T + 51, 1, 0)));
    }

    @Test
    void rejectsTimestampsBeyondTheCeiling() {
        FakeSlot slot = new FakeSlot(1, Long.MIN_VALUE, T0 + 10);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, ClockRegressionPolicy.FAIL);
        generator.generate();
        time.set(T0 + 10);
        generator.generate();
        // A pause (GC, suspended VM) past the expiry: the clock is now beyond the ceiling.
        time.set(T0 + 11);
        SlotWindowExceededException error = assertThrows(SlotWindowExceededException.class, generator::generate);
        assertThat(error.timestampMillis(), is(T0 + 11));
        assertThat(error.ceiling(), is(T0 + 10));
        assertThat(listener.events, hasItem("window"));
        assertThat(generator.lastIssuedMillis(), is(T0 + 10));
    }

    @Test
    void waitsUpToWaitUpToForARenewal() throws Exception {
        FakeSlot slot = new FakeSlot(1, Long.MIN_VALUE, T0 + 10);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, WAIT_200MS);
        time.set(T0 + 11);
        Future<Long> next = blocked(generator::generate);
        slot.raiseCeiling(T0 + 1000);
        assertThat(Layout.SNOWFLAKE_MS.unpack(done(next)), is(new IdParts(T + 11, 1, 0)));
    }

    @Test
    void failsWhenNoRenewalComesInWaitUpTo() {
        FakeSlot slot = new FakeSlot(1, Long.MIN_VALUE, T0 + 10);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, WAIT_200MS);
        time.set(T0 + 11);
        long started = System.nanoTime();
        assertThrows(SlotWindowExceededException.class, generator::generate);
        assertThat(System.nanoTime() - started, greaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(200)));
    }

    @Test
    void aNewBindingDuringTheWaitIsTaken() throws Exception {
        FakeSlot slot = new FakeSlot(1, Long.MIN_VALUE, T0 + 10);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, WAIT_200MS);
        time.set(T0 + 11);
        Future<Long> next = blocked(generator::generate);
        slot.rebind(2, T0, T0 + 1000);
        assertThat(Layout.SNOWFLAKE_MS.unpack(done(next)), is(new IdParts(T + 11, 2, 0)));
    }

    @Test
    void secondsLayout() throws Exception {
        long second = EPOCH + 1_000_000L * 1000;
        time.set(second + 500);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_S);
        assertThat(Layout.SNOWFLAKE_S.unpack(generator.generate()), is(new IdParts(1_000_000, 5, 0)));
        time.advance(400);
        assertThat(Layout.SNOWFLAKE_S.unpack(generator.generate()), is(new IdParts(1_000_000, 5, 1)));
        time.advance(100);
        assertThat(Layout.SNOWFLAKE_S.unpack(generator.generate()), is(new IdParts(1_000_001, 5, 2)));
        assertThat(generator.lastIssuedMillis(), is(second + 1000));
    }

    @Test
    void secondsLayoutChecksTheWholeSecondAgainstTheWindow() throws Exception {
        long second = EPOCH + 1_000_000L * 1000;
        time.set(second + 300);
        FakeSlot slot = new FakeSlot(1, second + 200, second + 1500);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_S, slot, ClockRegressionPolicy.FAIL);
        // The second starting at 'second' covers the floor: wait for the next one.
        Future<Long> first = blocked(generator::generate);
        time.set(second + 999);
        Thread.sleep(30);
        assertThat(first.isDone(), is(false));
        time.set(second + 1000);
        ExecutionException error = assertThrows(ExecutionException.class, () -> done(first));
        // That second ends at second + 1999, beyond the ceiling.
        assertThat(error.getCause() instanceof SlotWindowExceededException, is(true));
        slot.raiseCeiling(second + 1999);
        assertThat(Layout.SNOWFLAKE_S.unpack(generator.generate()), is(new IdParts(1_000_001, 1, 0)));
    }

    @Test
    void jsSafeLayout() {
        TimeBasedGenerator generator = generator(Layout.JS_SAFE_MS, FakeSlot.forever(7), ClockRegressionPolicy.DEFAULT);
        time.set(Instant.parse("2080-01-01T00:00:00Z").toEpochMilli());
        for (int i = 0; i < 1000; i++) {
            time.advance(1);
            long id = generator.generate();
            assertThat(Layout.isJsSafe(id), is(true));
            assertThat(Layout.JS_SAFE_MS.unpack(id).node(), is(7));
        }
        TimeBasedGenerator tooWide = generator(Layout.JS_SAFE_MS, FakeSlot.forever(8), ClockRegressionPolicy.DEFAULT);
        assertThrows(IllegalStateException.class, tooWide::generate);
    }

    @Test
    void cosId80IssuesPartsOnly() {
        TimeBasedGenerator generator = generator(Layout.COSID_80, FakeSlot.forever(1_000_000), ClockRegressionPolicy.DEFAULT);
        assertThrows(IllegalStateException.class, generator::generate);
        assertThat(generator.nextParts(), is(new IdParts(T0, 1_000_000, 0)));
        assertThat(generator.nextParts(), is(new IdParts(T0, 1_000_000, 1)));
        assertThat(generator.lastIssuedMillis(), is(T0));
    }

    @Test
    void timeBeyondTheLayoutFails() {
        Layout short10 = new Layout(Layout.COSID_EPOCH, ChronoUnit.MILLIS, 10, 10, 12, 0);
        assertThrows(IllegalStateException.class, generator(short10)::generate);
        time.set(EPOCH - 1);
        assertThrows(IllegalStateException.class, generator(Layout.SNOWFLAKE_MS)::generate);
    }

    @Test
    void rebindResetsTheSequenceWithTheNewSlot() {
        FakeSlot slot = FakeSlot.forever(1);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, ClockRegressionPolicy.DEFAULT);
        generator.generate();
        generator.generate();
        slot.rebind(2, Long.MIN_VALUE, Long.MAX_VALUE);
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()), is(new IdParts(T, 2, 0)));
        assertThat(generator.slot(), is(2));
        assertThat(generator.lastIssuedMillis(), is(T0));
    }

    @Test
    void racingThreadsResetARebindOnce() throws Exception {
        FakeSlot slot = FakeSlot.forever(1);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, ClockRegressionPolicy.DEFAULT);
        for (int i = 0; i < 10; i++) {
            generator.generate();
        }
        int threads = 16;
        int perThread = 50;
        for (int round = 0; round < 20; round++) {
            slot.rebind(round % 2 == 0 ? 2 : 3, Long.MIN_VALUE, Long.MAX_VALUE);
            CyclicBarrier start = new CyclicBarrier(threads);
            List<Future<long[]>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    long[] ids = new long[perThread];
                    for (int i = 0; i < perThread; i++) {
                        ids[i] = generator.generate();
                    }
                    return ids;
                }));
            }
            Set<Long> seqs = new TreeSet<>();
            for (Future<long[]> future : futures) {
                for (long id : done(future)) {
                    seqs.add(Layout.SNOWFLAKE_MS.unpack(id).seq());
                }
            }
            // One reset: the sequences of the new binding are exactly 0 .. n-1, no repeats.
            assertThat(seqs.size(), is(threads * perThread));
            assertThat(((TreeSet<Long>) seqs).last(), is((long) threads * perThread - 1));
        }
    }

    @Test
    void lostSlotFailsFast() {
        FakeSlot slot = FakeSlot.forever(1);
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS, slot, ClockRegressionPolicy.DEFAULT);
        generator.generate();
        slot.invalidate();
        SlotLostException error = assertThrows(SlotLostException.class, generator::generate);
        assertThat(error.slot(), is(1));
        assertThat(error.generator(), is("test"));
        slot.rebind(4, Long.MIN_VALUE, Long.MAX_VALUE);
        assertThat(Layout.SNOWFLAKE_MS.unpack(generator.generate()).node(), is(4));
    }

    @Test
    void closeStopsIssuing() {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS);
        generator.generate();
        time.advance(7);
        generator.generate();
        generator.close();
        assertThat(generator.lastIssuedMillis(), is(T0 + 7));
        assertThrows(IllegalStateException.class, generator::generate);
        assertThrows(IllegalStateException.class, generator::nextParts);
        generator.close();
        assertThat(generator.lastIssuedMillis(), is(T0 + 7));
    }

    @Test
    void closeBeforeAnyIdReportsNone() {
        TimeBasedGenerator generator = generator(Layout.SNOWFLAKE_MS);
        generator.close();
        assertThat(generator.lastIssuedMillis(), is(0L));
        assertThrows(IllegalStateException.class, generator::generate);
    }

    @Test
    void closeRaceIssuesNothingAfterCloseReturnsAndReportsTheExactLast() throws Exception {
        for (int round = 0; round < 10; round++) {
            FakeSlot slot = FakeSlot.forever(1);
            TimeBasedGenerator generator = new TimeBasedGenerator("race", Layout.SNOWFLAKE_MS, slot, TimeSource.SYSTEM,
                ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP);
            int threads = 8;
            AtomicInteger violations = new AtomicInteger();
            CountDownLatch running = new CountDownLatch(threads);
            boolean[] closed = new boolean[1];
            Object lock = new Object();
            List<Future<long[]>> futures = new ArrayList<>();
            int rebindRound = round;
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    long[] ids = new long[1 << 16];
                    int count = 0;
                    running.countDown();
                    for (; ; ) {
                        boolean closedBefore;
                        synchronized (lock) {
                            closedBefore = closed[0];
                        }
                        long id;
                        try {
                            id = generator.generate();
                        } catch (IllegalStateException e) {
                            return Arrays.copyOf(ids, count);
                        }
                        if (closedBefore) {
                            violations.incrementAndGet();
                        }
                        if (count == ids.length) {
                            ids = Arrays.copyOf(ids, count * 2);
                        }
                        ids[count++] = id;
                    }
                }));
            }
            running.await();
            Thread.sleep(5);
            if (rebindRound % 2 == 1) {
                slot.rebind(2, Long.MIN_VALUE, Long.MAX_VALUE);
                Thread.sleep(2);
            }
            generator.close();
            synchronized (lock) {
                closed[0] = true;
            }
            long maxTime = -1;
            Set<Long> unique = new HashSet<>();
            for (Future<long[]> future : futures) {
                for (long id : done(future)) {
                    assertThat(unique.add(id), is(true));
                    maxTime = Math.max(maxTime, Layout.SNOWFLAKE_MS.unpack(id).time());
                }
            }
            assertThat(violations.get(), is(0));
            assertThat(generator.lastIssuedMillis(), is(EPOCH + maxTime));
        }
    }

    @Test
    void manyThreadsIssueUniqueIds() throws Exception {
        assertUnique(Layout.SNOWFLAKE_MS, 8, 50_000);
        assertUnique(TINY, 8, 500);
    }

    private void assertUnique(Layout layout, int threads, int perThread) throws Exception {
        TimeBasedGenerator generator = new TimeBasedGenerator("stress", layout, FakeSlot.forever(3), TimeSource.SYSTEM,
            ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP);
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Future<long[]>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                start.await();
                long[] ids = new long[perThread];
                LongSupplier ids0 = generator::generate;
                for (int i = 0; i < perThread; i++) {
                    ids[i] = ids0.getAsLong();
                }
                return ids;
            }));
        }
        long[] all = new long[threads * perThread];
        int at = 0;
        for (Future<long[]> future : futures) {
            long[] ids = done(future);
            System.arraycopy(ids, 0, all, at, ids.length);
            at += ids.length;
        }
        Arrays.sort(all);
        for (int i = 1; i < all.length; i++) {
            assertThat("duplicate " + all[i], all[i] != all[i - 1], is(true));
        }
    }

    @Test
    void aWaitingThreadHoldsNoLock() throws Exception {
        // A floor wait, an exhausted sequence, a clock regression and a wait for a renewal at the ceiling.
        assertWaitsWithoutLock(generator(Layout.SNOWFLAKE_MS, new FakeSlot(1, T0 + 50, Long.MAX_VALUE), ClockRegressionPolicy.DEFAULT),
            () -> { });
        // Seconds, so the exhausted thread parks instead of spinning on sub-millisecond retries.
        TimeBasedGenerator tiny = generator(new Layout(Layout.COSID_EPOCH, ChronoUnit.SECONDS, 31, 10, 3, 3));
        assertWaitsWithoutLock(tiny, () -> {
            for (int seq = 0; seq <= 7; seq++) {
                tiny.generate();
            }
        });
        TimeBasedGenerator regressed = generator(Layout.SNOWFLAKE_MS);
        assertWaitsWithoutLock(regressed, () -> {
            regressed.generate();
            time.set(T0 - 100);
        });
        time.set(T0);
        TimeBasedGenerator renewal = generator(Layout.SNOWFLAKE_MS, new FakeSlot(1, Long.MIN_VALUE, T0 - 1),
            new ClockRegressionPolicy(Duration.ZERO, Duration.ofSeconds(30)));
        assertWaitsWithoutLock(renewal, () -> { });
    }

    /**
     * Makes {@code generator} wait on another thread, checks that the thread holds no monitor or lock while waiting,
     * and that {@code close()} on this thread neither blocks nor lets the waiting thread issue.
     */
    private void assertWaitsWithoutLock(TimeBasedGenerator generator, Runnable prepare) throws Exception {
        prepare.run();
        java.util.concurrent.FutureTask<Long> task = new java.util.concurrent.FutureTask<>(generator::generate);
        Thread waiting = new Thread(task, "waiting");
        waiting.start();
        java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 100 && waiting.getState() == Thread.State.RUNNABLE; i++) {
            Thread.sleep(5);
        }
        Thread.sleep(50);
        assertThat("still waiting", task.isDone(), is(false));
        // Between waits the thread retries, taking the lock for one short attempt: most samples find it holding nothing.
        int free = 0;
        for (int i = 0; i < 20; i++) {
            java.lang.management.ThreadInfo info = threads.getThreadInfo(new long[] {waiting.threadId()}, true, true)[0];
            if (info.getLockedMonitors().length == 0 && info.getLockedSynchronizers().length == 0) {
                free++;
            }
            Thread.sleep(2);
        }
        assertThat(free, greaterThanOrEqualTo(18));
        long started = System.nanoTime();
        generator.close();
        assertThat(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(500), is(true));
        ExecutionException error = assertThrows(ExecutionException.class, () -> task.get(5, TimeUnit.SECONDS));
        assertThat(error.getCause() instanceof IllegalStateException, is(true));
    }
}
