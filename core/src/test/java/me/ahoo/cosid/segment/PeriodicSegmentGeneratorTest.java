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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.observe.GeneratorListener;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

class PeriodicSegmentGeneratorTest {
    private static final Instant MORNING = Instant.parse("2026-10-26T10:00:00Z");
    private static final Instant MIDNIGHT = Instant.parse("2026-10-27T00:00:00Z");
    private final FakeRangeStore store = new FakeRangeStore();
    private final ManualTime time = new ManualTime(MORNING);
    private final BatchPolicy batch = BatchPolicy.builder()
        .step(10)
        .initialOffset(0)
        .lookahead(Duration.ofSeconds(1))
        .maxBatch(10)
        .reserveTimeout(Duration.ofMillis(200))
        .build();

    private PeriodicSegmentGenerator generator(PeriodicPolicy periodic, Executor executor) {
        return new PeriodicSegmentGenerator("bill", "app/bill", Period.YEAR_MONTH_DAY, ZoneOffset.UTC, store, batch, periodic, executor,
            time, GeneratorListener.NOOP);
    }

    @Test
    void issuesWithinThePeriodAtItsOwnKey() {
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            assertThat(store.reserves.get(), is(0));
            assertThat(generator.generate(), is(new GroupedId("20261026", 0)));
            assertThat(generator.generate(), is(new GroupedId("20261026", 1)));
            assertThat(store.exists("app/bill/20261026"), is(true));
            assertThat(generator.currentPeriod(), is("20261026"));
            assertThat(generator.nextGenerator(), nullValue());
            assertThat(generator.periodic(), is(PeriodicPolicy.DEFAULT));
        }
    }

    @Test
    void defaultsNeverIssueZeroInAnyPeriod() {
        try (PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", Period.YEAR_MONTH_DAY, ZoneOffset.UTC,
            store, BatchPolicy.DEFAULT, PeriodicPolicy.DEFAULT, Runnable::run, time, GeneratorListener.NOOP)) {
            assertThat(generator.generate(), is(new GroupedId("20261026", 1)));
            time.set(MIDNIGHT);
            assertThat(generator.generate(), is(new GroupedId("20261027", 1)));
        }
    }

    @Test
    void prewarmsTheNextPeriodSoTheBoundaryNeedsNoStoreCall() {
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            generator.generate();
            time.set(MIDNIGHT.minusSeconds(61));
            generator.generate();
            assertThat("not within the prewarm yet", store.exists("app/bill/20261027"), is(false));
            time.set(MIDNIGHT.minusSeconds(30));
            assertThat(generator.generate().period(), is("20261026"));
            assertThat(store.exists("app/bill/20261027"), is(true));
            SegmentGenerator next = generator.nextGenerator();
            assertThat(next, notNullValue());
            // Within the prewarm the hot path no longer takes the lock until the boundary.
            for (int i = 0; i < 5; i++) {
                assertThat(generator.generate().period(), is("20261026"));
            }
            // From now on the store blocks: the boundary must be crossed without calling it synchronously.
            CountDownLatch gate = new CountDownLatch(1);
            store.gate = gate;
            int reserves = store.reserves.get();
            time.set(MIDNIGHT);
            assertThat(generator.generate(), is(new GroupedId("20261027", 0)));
            assertThat(store.reserves.get(), is(reserves));
            gate.countDown();
            assertThat(generator.currentPeriod(), is("20261027"));
            assertThat(generator.nextGenerator(), nullValue());
        }
    }

    @Test
    void neverReturnsToAPeriodItLeft() {
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            generator.generate();
            time.set(MIDNIGHT);
            assertThat(generator.generate().period(), is("20261027"));
            time.set(MIDNIGHT.minusSeconds(1));
            assertThat(generator.generate().period(), is("20261027"));
        }
    }

    @Test
    void skipsPeriodsWhenIdleAcrossSeveral() {
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            generator.generate();
            time.set(MIDNIGHT.minusSeconds(30));
            generator.generate();
            SegmentGenerator prewarmed = generator.nextGenerator();
            time.set(MIDNIGHT.plus(Duration.ofDays(3)).plusSeconds(5));
            assertThat(generator.generate(), is(new GroupedId("20261030", 0)));
            assertThat(prewarmed.isClosed(), is(true));
        }
    }

    @Test
    void sealsTheOldKeyOnlyAfterSealAfter() {
        PeriodicPolicy periodic = PeriodicPolicy.builder().prewarm(Duration.ofMinutes(1)).sealAfter(Duration.ofHours(1)).build();
        try (PeriodicSegmentGenerator generator = generator(periodic, Runnable::run)) {
            generator.generate();
            time.set(MIDNIGHT);
            generator.generate();
            time.set(MIDNIGHT.plus(Duration.ofHours(1)).minusMillis(1));
            generator.generate();
            assertThat(store.sealCalls(), empty());
            time.set(MIDNIGHT.plus(Duration.ofHours(1)));
            generator.generate();
            assertThat(store.sealCalls(), contains("app/bill/20261026"));
            generator.generate();
            assertThat(store.sealCalls(), contains("app/bill/20261026"));
        }
    }

    @Test
    void withoutSealAfterNeverSeals() {
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            generator.generate();
            time.set(MIDNIGHT.plus(Duration.ofDays(400)));
            generator.generate();
            assertThat(store.sealCalls(), empty());
        }
    }

    @Test
    void sealFailureOrRejectionIsOnlyLogged() {
        PeriodicPolicy periodic = PeriodicPolicy.builder().prewarm(Duration.ZERO).sealAfter(Duration.ZERO).build();
        RangeStore failing = new RangeStore() {
            @Override
            public Range reserve(String key, long size, long initialOffset) {
                return store.reserve(key, size, initialOffset);
            }

            @Override
            public boolean seal(String key) {
                throw new IllegalStateException("seal failed");
            }
        };
        try (PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", Period.YEAR_MONTH_DAY, ZoneOffset.UTC,
            failing, batch, periodic, Runnable::run, time, GeneratorListener.NOOP)) {
            generator.generate();
            time.set(MIDNIGHT);
            assertThat(generator.generate().period(), is("20261027"));
        }
        boolean[] reject = {false};
        Executor rejecting = command -> {
            if (reject[0]) {
                throw new java.util.concurrent.RejectedExecutionException("shut down");
            }
            command.run();
        };
        time.set(MORNING);
        try (PeriodicSegmentGenerator generator = generator(periodic, rejecting)) {
            generator.generate();
            time.set(MIDNIGHT.plusSeconds(1));
            reject[0] = true;
            // The seal is not started; the new period's caller reserves itself.
            assertThat(generator.generate().period(), is("20261027"));
            assertThat(store.sealCalls(), empty());
        }
    }

    @Test
    void sealedKeyIsRefusedNeverRecreated() {
        store.sealNow("app/bill/20261026");
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            RangeSealedException e = assertThrows(RangeSealedException.class, generator::generate);
            assertThat(e.key(), is("app/bill/20261026"));
            time.advance(1000);
            assertThrows(RangeSealedException.class, generator::generate);
            assertThat(store.exists("app/bill/20261026"), is(false));
            // The next period is a key of its own.
            time.set(MIDNIGHT);
            assertThat(generator.generate(), is(new GroupedId("20261027", 0)));
        }
    }

    @Test
    void sealedNextPeriodFailsAtTheBoundary() {
        store.sealNow("app/bill/20261027");
        try (PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run)) {
            time.set(MIDNIGHT.minusSeconds(30));
            generator.generate();
            time.set(MIDNIGHT);
            assertThrows(RangeSealedException.class, generator::generate);
        }
    }

    @Test
    void closeClosesBothPeriods() {
        PeriodicSegmentGenerator generator = generator(PeriodicPolicy.DEFAULT, Runnable::run);
        time.set(MIDNIGHT.minusSeconds(30));
        generator.generate();
        SegmentGenerator next = generator.nextGenerator();
        generator.close();
        assertThat(next.isClosed(), is(true));
        assertThrows(IllegalStateException.class, generator::generate);
        generator.close();
        assertThrows(IllegalStateException.class, generator::generate);
        new PeriodicSegmentGenerator("bill", "app/bill", Period.YEAR_MONTH_DAY, ZoneOffset.UTC, store, batch, PeriodicPolicy.DEFAULT,
            Runnable::run, time, GeneratorListener.NOOP).close();
    }

    @Test
    void customPeriodWithAnEndBeforeTheInstantIsRefused() {
        Period broken = new Period() {
            @Override
            public String keyOf(Instant instant, ZoneId zone) {
                return "p";
            }

            @Override
            public Instant endOf(String key, ZoneId zone) {
                return Instant.EPOCH;
            }
        };
        try (PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", broken, ZoneOffset.UTC, store, batch,
            PeriodicPolicy.DEFAULT, Runnable::run, time, GeneratorListener.NOOP)) {
            assertThrows(IllegalStateException.class, generator::generate);
        }
    }

    @Test
    void aFaultyNextPeriodLeavesNothingBehindAcrossRetries() {
        // Day keys, but the second day's end is in the past until the period is repaired.
        boolean[] repaired = {false};
        Period faulty = new Period() {
            @Override
            public String keyOf(Instant instant, ZoneId zone) {
                return Period.YEAR_MONTH_DAY.keyOf(instant, zone);
            }

            @Override
            public Instant endOf(String key, ZoneId zone) {
                return key.equals("20261027") && !repaired[0] ? Instant.EPOCH : Period.YEAR_MONTH_DAY.endOf(key, zone);
            }
        };
        PeriodicPolicy sealAtOnce = PeriodicPolicy.builder().prewarm(Duration.ZERO).sealAfter(Duration.ZERO).build();
        try (PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", faulty, ZoneOffset.UTC, store, batch,
            sealAtOnce, Runnable::run, time, GeneratorListener.NOOP)) {
            assertThat(generator.generate().period(), is("20261026"));
            time.set(MIDNIGHT);
            for (int i = 0; i < 3; i++) {
                assertThrows(IllegalStateException.class, generator::generate);
            }
            // Each failure changed nothing: the old period is not sealed, the new key not created.
            assertThat(store.sealCalls(), empty());
            assertThat(store.exists("app/bill/20261027"), is(false));
            assertThat(generator.currentPeriod(), is("20261026"));
            repaired[0] = true;
            assertThat(generator.generate().period(), is("20261027"));
            // The old key is sealed once, not once per failed call.
            assertThat(store.sealCalls(), contains("app/bill/20261026"));
        }
    }

    @Test
    void customPeriodKeepsAPeriodWhoseKeyDoesNotChange() {
        // A period whose key stays the same over two ends: one key, two ends; the generator keeps issuing in it.
        Period sticky = new Period() {
            @Override
            public String keyOf(Instant instant, ZoneId zone) {
                return "all";
            }

            @Override
            public Instant endOf(String key, ZoneId zone) {
                return Instant.ofEpochMilli(time.currentMillis() + 1000);
            }
        };
        try (PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", sticky, ZoneOffset.UTC, store, batch,
            PeriodicPolicy.builder().prewarm(Duration.ofSeconds(2)).build(), Runnable::run, time, GeneratorListener.NOOP)) {
            assertThat(generator.generate(), is(new GroupedId("all", 0)));
            time.advance(1500);
            assertThat(generator.generate(), is(new GroupedId("all", 1)));
            assertThat(generator.nextGenerator(), nullValue());
        }
    }

    @Test
    void concurrentGenerationAcrossTheBoundaryIsUniquePerPeriod() throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(MIDNIGHT.minusMillis(200).toEpochMilli());
        BatchPolicy policy = BatchPolicy.builder()
            .step(10)
            .initialOffset(0)
            .lookahead(Duration.ofMillis(10))
            .maxBatch(100)
            .reserveTimeout(Duration.ofSeconds(5))
            .build();
        try (executor; PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "app/bill", Period.YEAR_MONTH_DAY,
            ZoneOffset.UTC, store, policy, PeriodicPolicy.builder().prewarm(Duration.ofMillis(100)).build(), executor,
            () -> clock.incrementAndGet() / 10, GeneratorListener.NOOP)) {
            clock.set(MIDNIGHT.minusMillis(200).toEpochMilli() * 10);
            int threads = 8;
            int perThread = 1000;
            java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(threads);
            List<java.util.concurrent.Future<List<GroupedId>>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(executor.submit(() -> {
                    start.await();
                    List<GroupedId> ids = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.generate());
                    }
                    return ids;
                }));
            }
            java.util.Set<GroupedId> all = new java.util.HashSet<>();
            for (var result : results) {
                all.addAll(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(all.size(), is(threads * perThread));
            assertThat(all.stream().anyMatch(id -> id.period().equals("20261026")), is(true));
            assertThat(all.stream().anyMatch(id -> id.period().equals("20261027")), is(true));
        }
    }
}
