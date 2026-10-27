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

package me.ahoo.cosid.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.Range;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

class SafeListenerTest {
    static final SlotLease LEASE = new SlotLease("app", 1, new Owner("a", UUID.randomUUID()), 1, 10, 0, 11, 5);

    static final List<Consumer<GeneratorListener>> EVENTS = List.of(
        l -> l.onRangeReserved("g", "k", new Range(0, 10), Duration.ZERO),
        l -> l.onRefillFailed("g", "k", new IllegalStateException()),
        l -> l.onBatchResized("g", 1, 2),
        l -> l.onSequenceExhausted("g"),
        l -> l.onClockRegression("g", 1, "wait"),
        l -> l.onWindowExceeded("g"),
        l -> l.onSlotAcquired(LEASE, "lowest"),
        l -> l.onSlotRenewed(LEASE),
        l -> l.onSlotRenewFailed(LEASE, new IllegalStateException()),
        l -> l.onSlotLost(LEASE),
        l -> l.onSlotReleased(LEASE),
        l -> l.onClockSkew("app", 3),
        l -> l.onDuplicateOwnerKey("app", "a"),
        l -> l.onStoreTimeout("db", "reserve"));

    /** Counts each event, then throws. */
    static final class Throwing implements GeneratorListener {
        final List<String> calls = new ArrayList<>();

        private void fail(String event) {
            calls.add(event);
            throw new IllegalStateException(event);
        }

        @Override
        public void onRangeReserved(String generator, String key, Range range, Duration latency) {
            fail("rangeReserved");
        }

        @Override
        public void onRefillFailed(String generator, String key, Throwable error) {
            fail("refillFailed");
        }

        @Override
        public void onBatchResized(String generator, long oldSize, long newSize) {
            fail("batchResized");
        }

        @Override
        public void onSequenceExhausted(String generator) {
            fail("sequenceExhausted");
        }

        @Override
        public void onClockRegression(String generator, long backwardsMillis, String action) {
            fail("clockRegression");
        }

        @Override
        public void onWindowExceeded(String generator) {
            fail("windowExceeded");
        }

        @Override
        public void onSlotAcquired(SlotLease lease, String reason) {
            fail("slotAcquired");
        }

        @Override
        public void onSlotRenewed(SlotLease lease) {
            fail("slotRenewed");
        }

        @Override
        public void onSlotRenewFailed(SlotLease lease, Throwable error) {
            fail("slotRenewFailed");
        }

        @Override
        public void onSlotLost(SlotLease lease) {
            fail("slotLost");
        }

        @Override
        public void onSlotReleased(SlotLease lease) {
            fail("slotReleased");
        }

        @Override
        public void onClockSkew(String namespace, long skewMillis) {
            fail("clockSkew");
        }

        @Override
        public void onDuplicateOwnerKey(String namespace, String ownerKey) {
            fail("duplicateOwnerKey");
        }

        @Override
        public void onStoreTimeout(String store, String operation) {
            fail("storeTimeout");
        }
    }

    @Test
    void everyEventReachesTheDelegateAndItsFailureStaysThere() {
        Throwing delegate = new Throwing();
        GeneratorListener safe = SafeListener.of(delegate);
        for (Consumer<GeneratorListener> event : EVENTS) {
            event.accept(safe);
        }
        assertThat(delegate.calls, contains("rangeReserved", "refillFailed", "batchResized", "sequenceExhausted", "clockRegression",
            "windowExceeded", "slotAcquired", "slotRenewed", "slotRenewFailed", "slotLost", "slotReleased", "clockSkew",
            "duplicateOwnerKey", "storeTimeout"));
    }

    @Test
    void noopAndWrappedListenersAreKept() {
        assertThat(SafeListener.of(GeneratorListener.NOOP), sameInstance(GeneratorListener.NOOP));
        GeneratorListener safe = SafeListener.of(new Throwing());
        assertThat(SafeListener.of(safe), sameInstance(safe));
        assertThat(safe instanceof SafeListener, is(true));
    }
}
