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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.migration.target.NamespaceState;
import me.ahoo.cosid.migration.target.RangeState;
import me.ahoo.cosid.migration.target.TargetAdmin;
import me.ahoo.cosid.migration.target.Targets;
import me.ahoo.cosid.store.Store;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The admin path of each 5.0 store: values only rise, seals stay, slots prepared for the 5.0 store code as it is.
 */
class TargetAdminTest {
    private static String namespace() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    @ParameterizedTest
    @EnumSource(TestBackend.class)
    void raisesRangesOnlyUpwardsAndKeepsSeals(TestBackend backend) {
        TestBackend.Store store = backend.store();
        String ns = namespace();
        try (TargetAdmin admin = Targets.open(store.location()); Store store5 = store.open5()) {
            assertThat(admin.now(), greaterThanOrEqualTo(System.currentTimeMillis() - 60_000));
            assertThat(admin.range(ns + "/order"), is(Optional.empty()));
            assertThat(admin.raise(ns + "/order", 100), is(new RangeState(ns + "/order", 100, false)));
            assertThat(admin.raise(ns + "/order", 50).next(), is(100L));
            assertThat(admin.raise(ns + "/order", 1L << 60).next(), is(1L << 60));
            // The 5.0 store reads what the admin path wrote.
            assertThat(store5.ranges().reserve(ns + "/order", 10, 0).start(), is(1L << 60));

            admin.raise(ns + "/bill/20240101", 7);
            store5.ranges().seal(ns + "/bill/20240101");
            store5.ranges().seal(ns + "/bill/20230101");
            admin.raise(ns + "/bill/20250101", 3);
            assertThat(admin.raise(ns + "/bill/20240101", 9), is(new RangeState(ns + "/bill/20240101", 9, true)));
            assertThat(admin.ranges(ns + "/bill/").stream().map(RangeState::key).toList(),
                is(List.of(ns + "/bill/20230101", ns + "/bill/20240101", ns + "/bill/20250101")));
            assertThat(admin.delete(ns + "/bill/20250101"), is(false));
            assertThat(admin.delete(ns + "/bill/20240101"), is(true));
            assertThat(admin.delete(ns + "/bill/20240101"), is(false));
            assertThat(admin.range(ns + "/bill/20240101"), is(Optional.empty()));
        }
    }

    @ParameterizedTest
    @EnumSource(TestBackend.class)
    void preparesSlotsTheStoreTakesAfterW(TestBackend backend) throws InterruptedException {
        TestBackend.Store store = backend.store();
        String ns = namespace();
        try (TargetAdmin admin = Targets.open(store.location()); Store store5 = store.open5()) {
            assertThat(admin.namespace(ns), is(Optional.empty()));
            long w1 = admin.now() + 5_000;
            NamespaceState prepared = admin.prepare(ns, 16, 1000, w1);
            assertThat(prepared.slots(), is(16));
            assertThat(prepared.minNextFloor(), is(w1));
            assertThat("a lower W changes nothing", admin.prepare(ns, 16, 1000, w1 - 1000).minNextFloor(), is(w1));
            IllegalStateException mismatch = assertThrows(IllegalStateException.class, () -> admin.prepare(ns, 32, 1000, w1));
            assertThat(mismatch.getMessage(), containsString("recorded with limit 16"));

            AcquireResult.Acquired first = (AcquireResult.Acquired) store5.slots().acquire(ns, new Owner("a", UUID.randomUUID()), 16,
                Duration.ofMillis(300), Duration.ofSeconds(1), OptionalInt.of(3));
            assertThat(first.lease().floor(), is(w1));
            IllegalStateException live = assertThrows(IllegalStateException.class, () -> admin.prepare(ns, 16, 1000, w1));
            assertThat(live.getMessage(), containsString("live slot lease"));

            // The holder dies; the next migration raises the slot it held, which stays free.
            Thread.sleep(400);
            long w2 = first.lease().ceiling() + 60_000;
            assertThat(admin.prepare(ns, 16, 1000, w2).minNextFloor(), greaterThanOrEqualTo(w2));
            AcquireResult.Acquired second = (AcquireResult.Acquired) store5.slots().acquire(ns, new Owner("b", UUID.randomUUID()), 16,
                Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.of(3));
            assertThat(second.lease().slot(), is(3));
            assertThat(second.lease().floor(), greaterThanOrEqualTo(w2));
        }
    }
}
