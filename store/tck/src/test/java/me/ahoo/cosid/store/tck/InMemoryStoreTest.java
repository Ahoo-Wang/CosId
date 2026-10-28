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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What the contracts leave open: argument validation and unknown namespaces.
 */
class InMemoryStoreTest {
    private final ManualClock clock = new ManualClock(1_000);
    private final InMemoryStore store = new InMemoryStore(clock);
    private final Owner owner = new Owner("host", UUID.randomUUID());

    @Test
    void servesBothPrimitives() {
        assertThat(store.ranges(), sameInstance(store));
        assertThat(store.slots(), sameInstance(store));
        assertThat(store.storeClock(), sameInstance(clock));
        assertThat(clock.advance(Duration.ofMillis(5)), is(1_005L));
        store.close();
    }

    @Test
    void validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> store.reserve("k", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> store.reserve("k", 1, -1));
        Duration ttl = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> store.acquire("ns", owner, 0, ttl, ttl, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.acquire("ns", owner, 1, Duration.ZERO, ttl, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.acquire("ns", owner, 1, ttl, Duration.ofMillis(-1), OptionalInt.empty()));
    }

    @Test
    void anUnknownNamespaceOrSlotHoldsNoLease() {
        SlotLease lease = SlotLease.builder().namespace("ns").slot(5).owner(owner).fence(1).expiresAt(0).floor(0).ceiling(0).storeNow(0).build();
        assertThat(store.renew(lease, Duration.ofSeconds(1), Duration.ZERO), is(Optional.empty()));
        assertThat(store.release(lease, 0), is(false));
        store.acquire("ns", owner, 2, Duration.ofSeconds(1), Duration.ZERO, OptionalInt.of(7));
        assertThat(store.renew(lease, Duration.ofSeconds(1), Duration.ZERO), is(Optional.empty()));
        assertThat(store.release(lease, 0), is(false));
    }
}
