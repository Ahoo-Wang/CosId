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

import me.ahoo.cosid.segment.RangeStore;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * The {@link RangeStore} contract (04-segment.md §1, 06-stores.md §3). A backend test extends it and implements
 * {@link #newStore()}.
 */
public abstract class RangeStoreContract {
    /**
     * A store on the backend under test. Each call opens a new instance on the same data, so tests can run several
     * instances against one key; tests close what they open.
     *
     * @return a new store instance
     */
    protected abstract RangeStore newStore();

    /** Range: the first range of a new key starts at initialOffset (S2). */
    @Test
    @Disabled("R1-C: the first range of a new key starts at initialOffset (S2)")
    public void firstReserveStartsAtInitialOffset() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Range: concurrent first reserves of one key create it once: one range starts at initialOffset, none overlap (S2). */
    @Test
    @Disabled("R1-C: concurrent first reserves of one key create it once: one range starts at initialOffset, none overlap (S2)")
    public void concurrentFirstReservesCreateTheKeyOnce() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Range: N threads on M store instances reserve one key: ranges are disjoint and strictly increase in completion order (S1, S4). */
    @Test
    @Disabled("R1-C: N threads on M store instances reserve one key: ranges are disjoint and strictly increase in completion order (S1, S4)")
    public void concurrentReservesAcrossStoresNeverOverlapAndStrictlyIncrease() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Range: ranges starting beyond 2^53 come back exact, with no floating point loss. */
    @Test
    @Disabled("R1-C: ranges starting beyond 2^53 come back exact, with no floating point loss")
    public void valuesBeyond2Pow53StayExact() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Range: a reservation passing Long.MAX_VALUE throws RangeExhaustedException and never wraps (S5). */
    @Test
    @Disabled("R1-C: a reservation passing Long.MAX_VALUE throws RangeExhaustedException and never wraps (S5)")
    public void overflowThrowsRangeExhausted() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Range: after seal, reserve throws RangeSealedException and the next value is unchanged; skipped when seal returns false (S6). */
    @Test
    @Disabled("R1-C: after seal, reserve throws RangeSealedException and the next value is unchanged; skipped when seal returns false (S6)")
    public void sealedKeyRejectsReserveAndKeepsItsValue() {
        throw new UnsupportedOperationException("R1-C");
    }

    /** Range: reserving key A then key B on the same connection gives B its own range (no stale last_insert_id). */
    @Test
    @Disabled("R1-C: reserving key A then key B on the same connection gives B its own range (no stale last_insert_id)")
    public void reservesOfDifferentKeysOnOneConnectionStayIndependent() {
        throw new UnsupportedOperationException("R1-C");
    }
}
