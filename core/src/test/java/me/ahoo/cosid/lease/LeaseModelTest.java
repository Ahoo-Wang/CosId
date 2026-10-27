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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

class LeaseModelTest {
    private static final Owner OWNER = new Owner("host-1", UUID.randomUUID());

    private final SlotLeaseStore store = new SlotLeaseStore() {
        @Override
        public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
            return new AcquireResult.Exhausted(0);
        }

        @Override
        public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
            return Optional.empty();
        }

        @Override
        public boolean release(SlotLease lease, long lastIssued) {
            return false;
        }
    };

    @Test
    void ownerAndLeaseValidate() {
        assertThrows(IllegalArgumentException.class, () -> new Owner(" ", UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> new Owner("host-1", null));
        SlotLease lease = new SlotLease("app", 3, OWNER, 1, 30_000, 0, 31_000, 0);
        assertThat(new AcquireResult.Acquired(lease, AcquireReason.LOWEST, false).lease(), is(lease));
        assertThat(AcquireReason.IDEMPOTENT.label(), is("idempotent"));
        assertThat(new AcquireResult.Exhausted(5).earliestExpiresAt(), is(5L));
        assertThrows(IllegalArgumentException.class, () -> new SlotLease("app", -1, OWNER, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlotLease("app", 1, OWNER, 1, 0, 10, 9, 0));
        assertThrows(NullPointerException.class, () -> new AcquireResult.Acquired(null, AcquireReason.LOWEST, false));
        assertThrows(NullPointerException.class, () -> new AcquireResult.Acquired(lease, null, false));
        store.close();
    }

    @Test
    void bindingHoldsItsWindow() {
        SlotBinding binding = new SlotBinding(3, 100, 200, 1);
        assertThat(binding.slot(), is(3));
        assertThat(binding.floor(), is(100L));
        assertThat(binding.ceiling(), is(200L));
        assertThat(binding.generation(), is(1L));
        assertThat(binding.isValid(), is(true));
        binding.invalidate();
        assertThat(binding.isValid(), is(false));
        assertThrows(IllegalArgumentException.class, () -> new SlotBinding(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlotBinding(0, 10, 9, 0));
    }

    @Test
    void leaseKeeperKeepsItsConfiguration() {
        LeaseKeeper keeper = new LeaseKeeper(store, "app", OWNER, 10, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty(),
            Duration.ofSeconds(30), Runnable::run, TimeSource.SYSTEM, GeneratorListener.NOOP);
        assertThat(keeper.namespace(), is("app"));
        assertThat(keeper.owner(), is(OWNER));
        assertThat(keeper.bits(), is(10));
        assertThrows(IllegalArgumentException.class, () -> new LeaseKeeper(store, "app", OWNER, 0, Duration.ofSeconds(30),
            Duration.ofSeconds(1), OptionalInt.empty(), Duration.ofSeconds(30), Runnable::run, TimeSource.SYSTEM, GeneratorListener.NOOP));
    }

    @Test
    void staticSlotValidatesTheSlot() {
        StaticSlot slot = new StaticSlot(3, 2, Optional.empty(), Duration.ofSeconds(10), Optional.empty(), Runnable::run, TimeSource.SYSTEM);
        assertThat(slot.slot(), is(3));
        assertThat(slot.bits(), is(2));
        assertThrows(IllegalArgumentException.class, () -> new StaticSlot(4, 2, Optional.empty(), Duration.ofSeconds(10),
            Optional.empty(), Runnable::run, TimeSource.SYSTEM));
        assertThrows(IllegalArgumentException.class, () -> new StaticSlot(0, 0, Optional.empty(), Duration.ofSeconds(10),
            Optional.empty(), Runnable::run, TimeSource.SYSTEM));
    }
}
