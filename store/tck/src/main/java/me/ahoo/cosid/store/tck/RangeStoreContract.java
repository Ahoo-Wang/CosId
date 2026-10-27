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

package me.ahoo.cosid.store.tck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The {@link RangeStore} contract (04-segment.md §1, 06-stores.md §3). A backend test extends it and implements
 * {@link #newStore()}. Every case uses keys of its own, so the cases share one backend safely.
 */
public abstract class RangeStoreContract {
    /**
     * A store on the backend under test. Each call opens a new instance on the same data, so tests can run several
     * instances against one key; tests close what they open.
     *
     * @return a new store instance
     */
    protected abstract RangeStore newStore();

    /**
     * How many threads the concurrency cases run.
     *
     * @return the thread count, 8 by default
     */
    protected int threads() {
        return 8;
    }

    /**
     * How many store instances the concurrency cases spread the threads over.
     *
     * @return the instance count, 4 by default
     */
    protected int storeInstances() {
        return 4;
    }

    /**
     * How many ranges each thread of {@link #concurrentReservesAcrossStoresNeverOverlapAndStrictlyIncrease()}
     * reserves.
     *
     * @return the count, 100 by default
     */
    protected int reservesPerThread() {
        return 100;
    }

    private static String newKey() {
        return "tck/range/" + UUID.randomUUID();
    }

    /** Range: the first range of a new key starts at initialOffset (S2). */
    @Test
    public void firstReserveStartsAtInitialOffset() {
        String key = newKey();
        try (RangeStore store = newStore()) {
            assertEquals(new Range(100, 110), store.reserve(key, 10, 100));
            assertEquals(new Range(110, 115), store.reserve(key, 5, 100));
            assertEquals(new Range(115, 125), store.reserve(key, 10, 0), "initialOffset only applies to a new key");
        }
    }

    /** Range: concurrent first reserves of one key create it once: one range starts at initialOffset, none overlap (S2). */
    @Test
    public void concurrentFirstReservesCreateTheKeyOnce() {
        String key = newKey();
        long initialOffset = 1_000;
        List<RangeStore> stores = Concurrently.open(storeInstances(), this::newStore);
        try {
            List<Range> ranges = Concurrently.run(threads(), i -> stores.get(i % stores.size()).reserve(key, 10, initialOffset));
            assertEquals(1, ranges.stream().filter(range -> range.start() == initialOffset).count(), () -> "exactly one range starts at the offset: " + ranges);
            assertTrue(ranges.stream().allMatch(range -> range.start() >= initialOffset), () -> "every range is at or past the offset: " + ranges);
            assertDisjoint(ranges);
        } finally {
            Concurrently.closeAll(stores);
        }
    }

    /** Range: N threads on M store instances reserve one key: ranges are disjoint and strictly increase in completion order (S1, S4). */
    @Test
    public void concurrentReservesAcrossStoresNeverOverlapAndStrictlyIncrease() {
        String key = newKey();
        int perThread = reservesPerThread();
        List<RangeStore> stores = Concurrently.open(storeInstances(), this::newStore);
        try {
            List<List<Range>> perThreadRanges = Concurrently.run(threads(), i -> {
                RangeStore store = stores.get(i % stores.size());
                List<Range> mine = new ArrayList<>(perThread);
                for (int n = 0; n < perThread; n++) {
                    mine.add(store.reserve(key, 1 + (n % 7), 0));
                }
                return mine;
            });
            List<Range> all = new ArrayList<>();
            for (List<Range> mine : perThreadRanges) {
                for (int n = 1; n < mine.size(); n++) {
                    assertTrue(mine.get(n).start() >= mine.get(n - 1).end(), "a later reserve of one thread lies after the earlier one: " + mine.get(n - 1) + ", " + mine.get(n));
                }
                all.addAll(mine);
            }
            assertEquals((long) threads() * perThread, all.size());
            assertDisjoint(all);
        } finally {
            Concurrently.closeAll(stores);
        }
    }

    /** Range: ranges starting beyond 2^53 come back exact, with no floating point loss. */
    @Test
    public void valuesBeyond2Pow53StayExact() {
        String key = newKey();
        long offset = (1L << 53) + 1;
        try (RangeStore store = newStore()) {
            assertEquals(new Range(offset, offset + 3), store.reserve(key, 3, offset));
            long size = (1L << 40) + 7;
            assertEquals(new Range(offset + 3, offset + 3 + size), store.reserve(key, size, offset));
            assertEquals(new Range(offset + 3 + size, offset + 3 + size + 1), store.reserve(key, 1, offset));
        }
    }

    /** Range: a reservation passing Long.MAX_VALUE throws RangeExhaustedException and never wraps (S5). */
    @Test
    public void overflowThrowsRangeExhausted() {
        String key = newKey();
        long offset = Long.MAX_VALUE - 10;
        try (RangeStore store = newStore()) {
            assertEquals(new Range(offset, offset + 5), store.reserve(key, 5, offset));
            assertThrows(RangeExhaustedException.class, () -> store.reserve(key, 10, offset));
            assertEquals(new Range(Long.MAX_VALUE - 5, Long.MAX_VALUE), store.reserve(key, 5, offset), "a failed reserve leaves the value as it was");
            assertThrows(RangeExhaustedException.class, () -> store.reserve(key, 1, offset));
            assertThrows(RangeExhaustedException.class, () -> store.reserve(key, Long.MAX_VALUE, offset));
        }
    }

    /** Range: after seal, reserve throws RangeSealedException and the next value is unchanged; skipped when seal returns false (S6). */
    @Test
    @OptionalCapability
    public void sealedKeyRejectsReserveAndKeepsItsValue() {
        String key = newKey();
        try (RangeStore store = newStore(); RangeStore other = newStore()) {
            store.reserve(key, 10, 0);
            boolean sealed = store.seal(key);
            Assumptions.assumeTrue(sealed, "the store does not support seal");
            assertThrows(RangeSealedException.class, () -> store.reserve(key, 10, 0));
            assertThrows(RangeSealedException.class, () -> other.reserve(key, 10, 0), "another instance sees the seal");
            assertTrue(store.seal(key), "sealing again keeps the key sealed");
            assertThrows(RangeSealedException.class, () -> store.reserve(key, 1, 0));

            String neverUsed = newKey();
            if (store.seal(neverUsed)) {
                assertThrows(RangeSealedException.class, () -> other.reserve(neverUsed, 1, 0), "a sealed key is never created");
            }
        }
    }

    /** Range: reserving key A then key B on the same connection gives B its own range (no stale last_insert_id). */
    @Test
    public void reservesOfDifferentKeysOnOneConnectionStayIndependent() {
        String first = newKey();
        String second = newKey();
        try (RangeStore store = newStore()) {
            assertEquals(new Range(0, 10), store.reserve(first, 10, 0));
            assertEquals(new Range(10, 20), store.reserve(first, 10, 0));
            assertEquals(new Range(5, 15), store.reserve(second, 10, 5), "a new key starts at its own offset");
            assertEquals(new Range(20, 30), store.reserve(first, 10, 0));
            assertEquals(new Range(15, 18), store.reserve(second, 3, 5));
        }
    }

    private static void assertDisjoint(List<Range> ranges) {
        List<Range> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingLong(Range::start));
        for (int i = 1; i < sorted.size(); i++) {
            Range previous = sorted.get(i - 1);
            Range next = sorted.get(i);
            assertTrue(next.start() >= previous.end(), () -> "ranges overlap: " + previous + " and " + next);
        }
    }
}
