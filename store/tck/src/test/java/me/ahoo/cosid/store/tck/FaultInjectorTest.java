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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.tck.FaultInjector.Operation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

class FaultInjectorTest {
    private final InMemoryStore memory = new InMemoryStore();
    private final FaultInjector faults = new FaultInjector();

    @Test
    void appliesOneShotFaultsInOrderThenTheStandingOne() {
        FaultyRangeStore store = new FaultyRangeStore(memory, faults);
        faults.once(Operation.RESERVE, new Fault.Fail(() -> new CosIdException("first")))
            .once(Operation.RESERVE, new Fault.LoseResponse(() -> new CosIdException("second")))
            .always(Operation.RESERVE, new Fault.Delay(Duration.ofMillis(20)));
        assertThat(assertThrows(CosIdException.class, () -> store.reserve("k", 10, 0)).getMessage(), is("first"));
        assertThat(faults.calls(Operation.RESERVE), is(0L));
        assertThat(assertThrows(CosIdException.class, () -> store.reserve("k", 10, 0)).getMessage(), is("second"));
        assertThat("the lost response was applied", faults.calls(Operation.RESERVE), is(1L));
        long started = System.nanoTime();
        assertThat("the replay sees the applied reserve", store.reserve("k", 10, 0), is(new Range(10, 20)));
        assertThat(System.nanoTime() - started, greaterThanOrEqualTo(Duration.ofMillis(20).toNanos()));
        faults.clear();
        assertThat(store.reserve("k", 10, 0), is(new Range(20, 30)));
        assertThat(store.seal("k"), is(true));
        assertThat(faults.calls(Operation.SEAL), is(1L));
        store.close();
    }

    @Test
    void wrapsEveryLeaseOperation() {
        FaultySlotLeaseStore store = new FaultySlotLeaseStore(memory, faults);
        Owner owner = new Owner("host", UUID.randomUUID());
        AcquireResult result = store.acquire("ns", owner, 2, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty());
        assertThat(result, instanceOf(AcquireResult.Acquired.class));
        SlotLease lease = ((AcquireResult.Acquired) result).lease();
        faults.once(Operation.RENEW, new Fault.LoseResponse(() -> new CosIdException("lost")));
        assertThrows(CosIdException.class, () -> store.renew(lease, Duration.ofSeconds(30), Duration.ofSeconds(1)));
        assertThat("a replayed renew succeeds with the same fence", store.renew(lease, Duration.ofSeconds(30), Duration.ofSeconds(1)).orElseThrow().fence(),
            is(lease.fence()));
        assertThat(store.release(lease, lease.ceiling()), is(true));
        assertThat(faults.calls(Operation.ACQUIRE), is(1L));
        assertThat(faults.calls(Operation.RENEW), is(2L));
        assertThat(faults.calls(Operation.RELEASE), is(1L));
        store.close();
    }

    @Test
    void anInterruptedDelayFails() {
        FaultyRangeStore store = new FaultyRangeStore(memory, faults);
        faults.once(Operation.RESERVE, new Fault.Delay(Duration.ofSeconds(10)));
        Thread.currentThread().interrupt();
        assertThrows(CosIdException.class, () -> store.reserve("k", 1, 0));
        assertThat(Thread.interrupted(), is(true));
    }
}
