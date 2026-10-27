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

package me.ahoo.cosid.lease;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.WindowFile;
import me.ahoo.cosid.store.tck.ManualClock;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@link StaticSlot} on a manual clock (05-machine-lease.md §4.4): the window is written ahead before use, extended
 * at {@code ahead/2}, and a restart, even after a crash and a clock regression, starts above every earlier window.
 */
class StaticSlotTest {
    private static final Duration AHEAD = Duration.ofSeconds(10);
    private static final long START = 1_700_000_000_000L;

    @TempDir
    Path dir;

    private final ManualClock clock = new ManualClock(START);
    private final ManualTicker ticker = new ManualTicker(clock);
    private final List<Runnable> tasks = new ArrayList<>();

    private Path file() {
        return dir.resolve("slot-3.window");
    }

    private StaticSlot slot(Optional<Instant> notBefore) {
        return new StaticSlot(3, 4, Optional.of(file()), AHEAD, notBefore, tasks::add, clock, ticker);
    }

    private long persisted() {
        return new WindowFile(file()).read().orElseThrow();
    }

    @Test
    void writesTheCeilingAheadBeforeUse() {
        StaticSlot slot = slot(Optional.empty());
        assertThrows(IllegalStateException.class, slot::current);
        assertThat(slot.extendIfDue(), is(-1L));
        slot.start();
        SlotBinding binding = slot.current();
        assertThat(binding.slot(), is(3));
        assertThat(binding.floor(), is(0L));
        assertThat(binding.ceiling(), is(START + AHEAD.toMillis()));
        assertThat("durable before the binding is published", persisted(), is(binding.ceiling()));
        assertThat(tasks.size(), is(1));
        assertThrows(IllegalStateException.class, slot::start);
        slot.close();
    }

    @Test
    void extendsTheWindowWhenLessThanHalfOfItIsLeft() {
        StaticSlot slot = slot(Optional.empty());
        slot.start();
        SlotBinding binding = slot.current();
        assertThat(slot.extendIfDue(), is(AHEAD.toNanos() / 4));
        ticker.advance(Duration.ofSeconds(4));
        slot.extendIfDue();
        assertThat("6s left: no write yet", binding.ceiling(), is(START + AHEAD.toMillis()));
        ticker.advance(Duration.ofSeconds(2));
        slot.extendIfDue();
        assertThat(binding.ceiling(), is(clock.currentMillis() + AHEAD.toMillis()));
        assertThat(persisted(), is(binding.ceiling()));
        assertThat(slot.current().generation(), is(1L));
        slot.close();
        assertThat(binding.isValid(), is(false));
        assertThat(slot.extendIfDue(), is(-1L));
    }

    @Test
    void aCrashAndAClockRegressionNeverRepeatAWindow() {
        StaticSlot first = slot(Optional.empty());
        first.start();
        ticker.advance(Duration.ofSeconds(6));
        first.extendIfDue();
        long highestCeiling = first.current().ceiling();
        // Crash: no close. The clock then goes back an hour.
        clock.set(START - Duration.ofHours(1).toMillis());
        StaticSlot second = slot(Optional.empty());
        second.start();
        SlotBinding binding = second.current();
        assertThat("every timestamp the first run could issue is at most the new floor", binding.floor(), greaterThanOrEqualTo(highestCeiling));
        assertThat(binding.ceiling(), greaterThanOrEqualTo(binding.floor()));
        assertThat(persisted(), greaterThanOrEqualTo(binding.floor()));
        second.close();
    }

    @Test
    void closeWritesTheExactLastIssuedForTheNextStart() {
        StaticSlot first = slot(Optional.empty());
        first.start();
        long lastIssued = START + 1_000;
        first.close(lastIssued);
        first.close(lastIssued);
        assertThat(persisted(), is(lastIssued));
        StaticSlot second = slot(Optional.empty());
        second.start();
        assertThat(second.current().floor(), is(lastIssued));
        long floor = second.current().floor();
        second.close(Long.MIN_VALUE);
        assertThat("nothing issued keeps the floor", persisted(), is(floor));
        StaticSlot third = slot(Optional.empty());
        third.start();
        long ceiling = third.current().ceiling();
        third.close();
        assertThat("close() keeps the ceiling written ahead", persisted(), is(ceiling));
    }

    @Test
    void startsAboveNotBeforeAndAboveTheFile() {
        long notBefore = START + Duration.ofMinutes(5).toMillis();
        StaticSlot fresh = slot(Optional.of(Instant.ofEpochMilli(notBefore)));
        fresh.start();
        assertThat(fresh.current().floor(), is(notBefore));
        assertThat("the clock is behind the floor: the window starts at the floor", fresh.current().ceiling(), is(notBefore));
        fresh.close(Long.MIN_VALUE);
        StaticSlot lowerNotBefore = slot(Optional.of(Instant.ofEpochMilli(START)));
        lowerNotBefore.start();
        assertThat(lowerNotBefore.current().floor(), is(notBefore));
        lowerNotBefore.close();
    }

    @Test
    void withoutAFileTheWindowIsUnbounded() {
        StaticSlot slot = new StaticSlot(1, 2, Optional.empty(), AHEAD, Optional.of(Instant.ofEpochMilli(START)), tasks::add, clock);
        slot.start();
        assertThat(slot.current().floor(), is(START));
        assertThat(slot.current().ceiling(), is(Long.MAX_VALUE));
        assertThat(tasks.isEmpty(), is(true));
        assertThat(slot.extendIfDue(), is(-1L));
        slot.close(START + 5);
        assertThat(slot.current().isValid(), is(false));

        StaticSlot zero = new StaticSlot(1, 2, Optional.empty(), AHEAD, Optional.empty(), tasks::add, clock);
        zero.start();
        assertThat(zero.current().floor(), is(0L));
        zero.close();
    }

    @Test
    void aFailedExtensionBacksOffAndRetries() throws IOException {
        StaticSlot slot = slot(Optional.empty());
        slot.start();
        Path temporary = dir.resolve("slot-3.window.tmp");
        Files.createDirectory(temporary);
        ticker.advance(Duration.ofSeconds(6));
        assertThat(slot.extendIfDue(), is(Duration.ofMillis(100).toNanos()));
        assertThat(slot.current().ceiling(), is(START + AHEAD.toMillis()));
        assertThat(slot.extendIfDue(), is(Duration.ofMillis(100).toNanos()));
        ticker.advance(Duration.ofMillis(100));
        assertThat(slot.extendIfDue(), is(Duration.ofMillis(200).toNanos()));
        Files.delete(temporary);
        ticker.advance(Duration.ofMillis(200));
        slot.extendIfDue();
        assertThat(slot.current().ceiling(), is(clock.currentMillis() + AHEAD.toMillis()));
        Files.createDirectory(temporary);
        slot.close(START + 1);
        assertThat("a failed close keeps the ceiling written ahead", persisted(), is(slot.current().ceiling()));
    }

    @Test
    void refusesAMalformedFile() throws IOException {
        Files.writeString(file(), "ceiling=later\n");
        assertThrows(CosIdException.class, () -> slot(Optional.empty()).start());
    }

    @Test
    void rejectsAnExecutorThatRunsOnTheCallingThread() {
        StaticSlot inline = new StaticSlot(3, 4, Optional.of(file()), AHEAD, Optional.empty(), Runnable::run, clock, ticker);
        assertThrows(IllegalArgumentException.class, inline::start);
        StaticSlot rejected = new StaticSlot(3, 4, Optional.of(file()), AHEAD, Optional.empty(), task -> {
            throw new RejectedExecutionException("shut down");
        }, clock, ticker);
        assertThrows(CosIdException.class, rejected::start);
        assertThrows(IllegalArgumentException.class, () -> new StaticSlot(3, 4, Optional.of(file()), Duration.ofMillis(1), Optional.empty(), tasks::add, clock));
    }

    @Test
    void extendsOnARealExecutor() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            StaticSlot slot = new StaticSlot(3, 4, Optional.of(file()), Duration.ofMillis(200), Optional.empty(), executor, TimeSource.SYSTEM);
            slot.start();
            long first = slot.current().ceiling();
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (slot.current().ceiling() == first && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(slot.current().ceiling(), greaterThan(first));
            long ceiling = slot.current().ceiling();
            assertThat("written ahead: the file is never behind the binding", persisted(), greaterThanOrEqualTo(ceiling));
            slot.close();
        } finally {
            executor.shutdownNow();
        }
    }
}
