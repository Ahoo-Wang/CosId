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
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.StoreTimeoutException;
import me.ahoo.cosid.store.tck.Fault;
import me.ahoo.cosid.store.tck.FaultInjector;
import me.ahoo.cosid.store.tck.FaultyRangeStore;
import me.ahoo.cosid.store.tck.FaultySlotLeaseStore;
import me.ahoo.cosid.store.tck.InMemoryStore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

class ObservedStoresTest {
    private final List<String> timeouts = new CopyOnWriteArrayList<>();
    private final GeneratorListener listener = new GeneratorListener() {
        @Override
        public void onStoreTimeout(String store, String operation) {
            timeouts.add(store + ":" + operation);
        }
    };
    private final InMemoryStore memory = new InMemoryStore();
    private final FaultInjector faults = new FaultInjector();

    @Test
    void reportsEveryOperationThatTimesOutAndRethrowsTheFailure() {
        RangeStore ranges = ObservedStores.ranges("db", new FaultyRangeStore(memory, faults), listener);
        SlotLeaseStore slots = ObservedStores.slots("db", new FaultySlotLeaseStore(memory, faults), listener);
        Owner owner = new Owner("host", UUID.randomUUID());
        SlotLease lease = ((AcquireResult.Acquired) slots.acquire("ns", owner, 4, Duration.ofSeconds(30), Duration.ofSeconds(1),
            0,
            OptionalInt.empty())).lease();
        StoreTimeoutException timeout = new StoreTimeoutException("timed out");
        // A store's own exception carrying the timeout as its cause counts too.
        CosIdException wrapped = new CosIdException("store failed", new StoreTimeoutException("timed out"));
        for (FaultInjector.Operation operation : FaultInjector.Operation.values()) {
            faults.once(operation, new Fault.Fail(() -> operation == FaultInjector.Operation.SEAL ? wrapped : timeout));
        }
        assertThat(assertThrows(StoreTimeoutException.class, () -> ranges.reserve("k", 1, 0)), sameInstance(timeout));
        assertThat(assertThrows(CosIdException.class, () -> ranges.seal("k")), sameInstance(wrapped));
        assertThrows(StoreTimeoutException.class, () -> slots.acquire("ns", owner, 4, Duration.ofSeconds(30), Duration.ofSeconds(1), 0, OptionalInt.empty()));
        assertThrows(StoreTimeoutException.class, () -> slots.renew(lease, Duration.ofSeconds(30), Duration.ofSeconds(1)));
        assertThrows(StoreTimeoutException.class, () -> slots.release(lease, 0));
        assertThat(timeouts, contains("db:reserve", "db:seal", "db:acquire", "db:renew", "db:release"));
    }

    @Test
    void otherFailuresAndSuccessesAreNotReported() {
        RangeStore ranges = ObservedStores.ranges("db", new FaultyRangeStore(memory, faults), listener);
        assertThat(ranges.reserve("k", 2, 0), is(new Range(0, 2)));
        faults.once(FaultInjector.Operation.RESERVE, new Fault.Fail(() -> new CosIdException("refused")));
        assertThrows(CosIdException.class, () -> ranges.reserve("k", 1, 0));
        assertThat(timeouts.isEmpty(), is(true));
    }

    @Test
    void findsTheTimeoutAmongTheCauses() {
        assertThat(StoreTimeoutException.isTimeout(new StoreTimeoutException("t")), is(true));
        assertThat(StoreTimeoutException.isTimeout(new RuntimeException(new IllegalStateException(new StoreTimeoutException("t")))), is(true));
        assertThat(StoreTimeoutException.isTimeout(new RuntimeException("no")), is(false));
        assertThat(StoreTimeoutException.isTimeout(null), is(false));
    }
}
