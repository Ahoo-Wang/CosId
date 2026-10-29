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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.TimeOverflowException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The contract of {@link UuidV7Generator} (D46, RFC 9562 §5.7 and §6.2 method 1 with a 26-bit counter): the bits, the
 * order within an instance, the counter and when it carries, a clock that goes back, the limits, and uniqueness under
 * contention.
 */
class UuidV7GeneratorTest {
    private static final long NOW = 1_761_696_000_000L;
    private static final long COUNTER_MAX = (1L << UuidV7Generator.COUNTER_BITS) - 1;

    private static long millis(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }

    /** The 26-bit counter: the 12 bits of rand_a, then the top 14 bits of rand_b. */
    private static long counter(UUID id) {
        return (id.getMostSignificantBits() & 0xFFF) << 14 | (id.getLeastSignificantBits() >>> 48 & 0x3FFF);
    }

    @Test
    void versionVariantAndTimestampAreThoseOfRfc9562() {
        UuidV7Generator generator = new UuidV7Generator("ids", new ManualTimeSource(NOW));
        UUID id = generator.generateUuid();
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        assertEquals(NOW, millis(id));
        assertEquals(7, (int) (id.getMostSignificantBits() >>> 12 & 0xF));
        assertEquals(0b10, (int) (id.getLeastSignificantBits() >>> 62 & 0b11));
    }

    @Test
    void theTextIsTheCanonicalFormTheStandardLibraryParses() {
        UuidV7Generator generator = new UuidV7Generator("ids", TimeSource.SYSTEM);
        String text = generator.generate();
        assertEquals(36, text.length());
        assertTrue(text.matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), text);
        UUID parsed = UUID.fromString(text);
        assertEquals(7, parsed.version());
        assertEquals(2, parsed.variant());
        assertEquals(7, UUID.fromString(generator.get()).version(), "get() is the text too");
    }

    @Test
    void aStillClockKeepsItsTimestampFor100000IncreasingIds() {
        UuidV7Generator generator = new UuidV7Generator("ids", new ManualTimeSource(NOW));
        UUID previous = generator.generateUuid();
        assertThat(counter(previous), lessThan(UuidV7Generator.START_BOUND));
        for (int i = 0; i < 100_000; i++) {
            UUID next = generator.generateUuid();
            assertEquals(NOW, millis(next), "the timestamp does not run ahead");
            assertEquals(counter(previous) + 1, counter(next), "the counter counts within the millisecond");
            assertThat(next.compareTo(previous), greaterThan(0));
            assertThat(next.toString().compareTo(previous.toString()), greaterThan(0));
            previous = next;
        }
    }

    @Test
    void aNewMillisecondStartsTheCounterAtRandomBelowHalfItsRange() {
        ManualTimeSource time = new ManualTimeSource(NOW);
        UuidV7Generator generator = new UuidV7Generator("ids", time);
        UUID previous = generator.generateUuid();
        for (int i = 1; i <= 50; i++) {
            time.advance(1);
            UUID next = generator.generateUuid();
            assertEquals(NOW + i, millis(next));
            assertThat(counter(next), lessThan(UuidV7Generator.START_BOUND));
            assertThat(next.compareTo(previous), greaterThan(0));
            previous = next;
        }
    }

    @Test
    void onlyAFullCounterCarriesIntoTheNextMillisecond() {
        UuidV7Generator generator = new UuidV7Generator("ids", new ManualTimeSource(NOW));
        generator.state(NOW, COUNTER_MAX - 1);
        UUID last = generator.generateUuid();
        assertEquals(NOW, millis(last));
        assertEquals(COUNTER_MAX, counter(last));
        UUID carried = generator.generateUuid();
        assertEquals(NOW + 1, millis(carried), "the next millisecond, borrowed");
        assertEquals(0, counter(carried));
        assertThat(carried.compareTo(last), greaterThan(0));
    }

    @Test
    void aClockThatGoesBackKeepsTheLastTimestampAndCountsOn() {
        ManualTimeSource time = new ManualTimeSource(NOW);
        UuidV7Generator generator = new UuidV7Generator("ids", time);
        UUID before = generator.generateUuid();
        time.set(NOW - 5_000);
        UUID after = generator.generateUuid();
        assertEquals(NOW, millis(after));
        assertEquals(counter(before) + 1, counter(after));
        assertThat(after.compareTo(before), greaterThan(0));
        time.set(NOW + 1);
        assertEquals(NOW + 1, millis(generator.generateUuid()), "the clock caught up");
    }

    @Test
    void aTimestampPast48BitsFails() {
        UuidV7Generator generator = new UuidV7Generator("ids", new ManualTimeSource(1L << UuidV7Generator.TIMESTAMP_BITS));
        TimeOverflowException overflow = assertThrows(TimeOverflowException.class, generator::generateUuid);
        assertEquals("ids", overflow.generator());
        assertEquals(UuidV7Generator.TIMESTAMP_BITS, overflow.timeBits());
        assertThat(new UuidV7Generator("ids", new ManualTimeSource((1L << UuidV7Generator.TIMESTAMP_BITS) - 1)).generateUuid().version(), is(7));
    }

    @Test
    void anInstanceIssuesFor2Pow38MillisecondsAfterItsStart() {
        ManualTimeSource time = new ManualTimeSource(NOW);
        UuidV7Generator generator = new UuidV7Generator("ids", time);
        // The state counts from one millisecond before the start.
        time.set(NOW - 1 + (1L << UuidV7Generator.OFFSET_BITS) - 1);
        assertEquals(NOW - 1 + (1L << UuidV7Generator.OFFSET_BITS) - 1, millis(generator.generateUuid()));
        time.set(NOW - 1 + (1L << UuidV7Generator.OFFSET_BITS));
        assertEquals(UuidV7Generator.OFFSET_BITS, assertThrows(TimeOverflowException.class, generator::generateUuid).timeBits());
    }

    @Test
    void concurrentIdsAreUniqueAndIncreaseWithinEachThread() throws Exception {
        UuidV7Generator generator = new UuidV7Generator("ids", TimeSource.SYSTEM);
        int threads = 8;
        int perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<List<UUID>>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    List<UUID> ids = new ArrayList<>(perThread);
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.generateUuid());
                    }
                    return ids;
                }));
            }
            start.countDown();
            Set<UUID> all = new HashSet<>();
            for (Future<List<UUID>> result : results) {
                List<UUID> ids = result.get();
                for (int i = 1; i < ids.size(); i++) {
                    assertThat(ids.get(i).compareTo(ids.get(i - 1)), greaterThan(0));
                }
                all.addAll(ids);
            }
            assertEquals(threads * perThread, all.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
