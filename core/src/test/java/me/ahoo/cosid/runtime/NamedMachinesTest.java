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

package me.ahoo.cosid.runtime;

import static me.ahoo.cosid.runtime.CosIdRuntimeTest.segment;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.snowflake;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.store;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.store.tck.ManualClock;
import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Named machines and the slot offset (D37, api-freeze.md §2): one runtime, several slots in namespaces of their own.
 */
class NamedMachinesTest {
    static final long T0 = 1_800_000_000_000L;

    final List<String> events = new CopyOnWriteArrayList<>();
    final ManualClock clock = new ManualClock(T0);
    final TestStores memory = new TestStores("memory", clock, events);
    final GeneratorListener listener = new GeneratorListener() {
        @Override
        public void onSlotAcquired(SlotLease lease, String reason) {
            events.add("acquired:" + lease.namespace());
        }

        @Override
        public void onSlotReleased(SlotLease lease) {
            events.add("released:" + lease.namespace());
        }
    };

    RuntimeOptions options() {
        return RuntimeOptions.builder().time(clock).listener(listener).storeProvider(memory).hostResolver(() -> "host").build();
    }

    static MachineSpec.Leased leased(String store, String namespace) {
        return MachineSpec.Leased.builder(store).bits(2).namespace(namespace).build();
    }

    /** The service machine in [app], a global one in [global-ids] for the 80-bit ids. */
    RuntimeSpec.Builder twoMachines() {
        return RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .store("cache", store("memory"))
            .machine(MachineSpec.Leased.builder("db").bits(2).build())
            .machine("global", leased("cache", "global-ids"))
            .generator("trace", snowflake(Layout.SNOWFLAKE_MS))
            .generator("token", GeneratorSpec.CosId80.builder().machine("global").build());
    }

    String problems(RuntimeSpec spec) {
        CosIdException invalid = assertThrows(CosIdException.class, () -> CosIdRuntime.start(spec, options()));
        assertThat(memory.events, is(empty()));
        return invalid.getMessage();
    }

    @Test
    void eachMachineLeasesInItsOwnNamespace() {
        // Another holder in the service namespace: the two namespaces do not see each other's slots.
        memory.backing("db").acquire("app", new Owner("other", UUID.randomUUID()), 4, Duration.ofSeconds(30), Duration.ofSeconds(1), 0,
            OptionalInt.empty());
        try (CosIdRuntime runtime = CosIdRuntime.start(twoMachines().build(), options())) {
            assertThat(runtime.machines(), is(Set.of("default", "global")));
            SlotLease service = runtime.slot().orElseThrow();
            SlotLease global = runtime.slot("global").orElseThrow();
            assertThat(runtime.slot("default"), is(Optional.of(service)));
            assertThat(service.namespace(), is("app"));
            assertThat(service.slot(), is(1));
            assertThat(global.namespace(), is("global-ids"));
            assertThat(global.slot(), is(0));
            assertThat(runtime.slot("nope"), is(Optional.empty()));
            // The default machine starts first, then the named ones.
            assertThat(events.stream().filter(event -> event.startsWith("acquired:")).toList(), contains("acquired:app", "acquired:global-ids"));

            assertThat(Layout.SNOWFLAKE_MS.unpack(runtime.generator("trace").generate()).node(), is(1));
            assertThat(runtime.get("token", PartsIdGenerator.class).nextParts().node(), is(0));
            Map<String, Object> token = runtime.describe().get("token").details();
            assertThat(token.get("machine"), is("global"));
            assertThat(token.get("machineNamespace"), is("global-ids"));
            assertThat(token.get("node"), is(0));
            assertThat(runtime.describe().get("trace").details().get("machine"), is("default"));
            assertThat(runtime.describe().get("trace").details().get("machineNamespace"), is("app"));
        }
    }

    @Test
    void theSlotOffsetMovesTheNodes() {
        RuntimeSpec spec = RuntimeSpec.builder("app")
            .store("cache", store("memory"))
            .machine(MachineSpec.Leased.builder("cache").bits(2).slotOffset(512).build())
            .generator("trace", snowflake(Layout.SNOWFLAKE_MS))
            .generator("token", GeneratorSpec.CosId80.builder().build())
            .build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options())) {
            SlotLease lease = runtime.slot().orElseThrow();
            assertThat(lease.slot(), is(0));
            assertThat(Layout.SNOWFLAKE_MS.unpack(runtime.generator("trace").generate()).node(), is(512));
            IdParts parts = runtime.get("token", PartsIdGenerator.class).nextParts();
            assertThat(parts.node(), is(512));
            assertThat(runtime.describe().get("trace").details().get("node"), is(512));
            assertThat(runtime.describe().get("trace").details().get("slot"), is(0));
        }
        // The store recorded the offset: another client with another offset is refused.
        assertThrows(me.ahoo.cosid.NamespaceMismatchException.class, () -> memory.backing("cache").acquire("app", new Owner("x", UUID.randomUUID()), 4,
            Duration.ofSeconds(30), Duration.ofSeconds(1), 0, OptionalInt.empty()));
    }

    @Test
    void theNodeBitsMustHoldTheOffsetSlotsForEveryGenerator() {
        Layout tenNodes = new Layout(Layout.COSID_EPOCH, ChronoUnit.MILLIS, 41, 10, 12, 0);
        Layout twelveNodes = new Layout(Layout.COSID_EPOCH, ChronoUnit.MILLIS, 41, 12, 10, 0);
        String message = problems(RuntimeSpec.builder("app")
            .store("cache", store("memory"))
            .machine(MachineSpec.Leased.builder("cache").bits(2).slotOffset(1022).build())
            .generator("fits", snowflake(twelveNodes))
            .generator("narrow", snowflake(tenNodes))
            .generator("also", snowflake(tenNodes))
            .build());
        assertThat(message, containsString("generator [narrow]: the layout has 10 node bits, too few for slotOffset 1022 + 2^2 slots of machine [default]"));
        assertThat(message, containsString("generator [also]: the layout has 10 node bits, too few for slotOffset 1022 + 2^2 slots of machine [default]"));
        assertThat(message, not(containsString("generator [fits]")));
        // 1020 + 4 = 1024 = 2^10 just fits.
        RuntimeSpec fits = RuntimeSpec.builder("app")
            .store("cache", store("memory"))
            .machine(MachineSpec.Leased.builder("cache").bits(2).slotOffset(1020).build())
            .generator("narrow", snowflake(tenNodes))
            .build();
        try (CosIdRuntime runtime = CosIdRuntime.start(fits, options())) {
            assertThat(tenNodes.unpack(runtime.generator("narrow").generate()).node(), is(1020));
        }
    }

    @Test
    void machinesAreChecked() {
        String message = problems(RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .machine(MachineSpec.Leased.builder("db").bits(2).build())
            .machine("same", MachineSpec.Leased.builder("db").bits(2).build())
            .machine("default", leased("db", "other"))
            .machine("a/b", leased("db", "slash"))
            .machine("bad", MachineSpec.Leased.builder("nowhere").bits(2).slotOffset(-1).namespace("bad-ns").build())
            .generator("x", snowflake(Layout.SNOWFLAKE_MS).withMachine("same"))
            .generator("y", snowflake(Layout.SNOWFLAKE_MS).withMachine("missing"))
            .generator("z", snowflake(Layout.SNOWFLAKE_MS).withMachine("bad"))
            .generator("w", snowflake(Layout.SNOWFLAKE_MS))
            .build());
        assertThat(message, containsString("machines.same: namespace [app] is also the namespace of machine [default]: each machine needs its own"));
        assertThat(message, containsString("machines.default: the name must not be blank, contain '/' nor be [default]"));
        assertThat(message, containsString("machines.a/b: the name must not be blank, contain '/' nor be [default]"));
        assertThat(message, containsString("generator [y]: no machine [missing]"));
        assertThat(message, containsString("machines.bad: no store [nowhere]"));
        assertThat(message, containsString("machines.bad: slotOffset must not be negative: -1"));
    }

    @Test
    void twoStaticMachinesNeedTwoStateFiles() {
        Path state = Path.of("build", "state-" + UUID.randomUUID());
        String message = problems(RuntimeSpec.builder("app")
            .machine(MachineSpec.Static.builder(1).stateFile(state).build())
            .machine("global", MachineSpec.Static.builder(2).stateFile(state).namespace("global-ids").build())
            .generator("a", snowflake(Layout.SNOWFLAKE_MS))
            .generator("b", snowflake(Layout.SNOWFLAKE_MS).withMachine("global"))
            .build());
        assertThat(message, containsString("machines.global: stateFile " + state + " is also the state file of machine [default]"));
    }

    @Test
    void anUnchosenMachineDoesNotStartNorOpenItsStore() {
        RuntimeSpec spec = RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .store("cache", store("memory"))
            .machine(MachineSpec.Leased.builder("db").bits(2).build())
            .machine("global", leased("cache", "global-ids"))
            .generator("trace", snowflake(Layout.SNOWFLAKE_MS))
            .generator("off", new GeneratorSpec.Disabled(GeneratorSpec.CosId80.builder().machine("global").build()))
            .build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options())) {
            assertThat(runtime.machines(), is(Set.of("default")));
            assertThat(runtime.slot("global"), is(Optional.empty()));
            assertThat(memory.events, not(hasItem("open:cache")));
        }
        // Without a time-based generator no machine starts at all.
        RuntimeSpec segments = RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .machine("global", leased("db", "global-ids"))
            .generator("order", segment("db"))
            .build();
        try (CosIdRuntime runtime = CosIdRuntime.start(segments, options())) {
            assertThat(runtime.machines(), is(Set.of()));
        }
    }

    @Test
    void aMachineThatFailsReleasesTheOnesStartedBefore() {
        for (int i = 0; i < 4; i++) {
            memory.backing("cache").acquire("global-ids", new Owner("other-" + i, UUID.randomUUID()), 4, Duration.ofSeconds(30),
                Duration.ofSeconds(1), 0, OptionalInt.empty());
        }
        RuntimeSpec spec = twoMachines().machine("global", MachineSpec.Leased.builder("cache").bits(2).namespace("global-ids")
            .acquireTimeout(Duration.ZERO).build()).build();
        assertThrows(CosIdException.class, () -> CosIdRuntime.start(spec, options()));
        assertThat(events.stream().filter(event -> event.startsWith("acquired:") || event.startsWith("released:")).toList(),
            contains("acquired:app", "released:app"));
        // The default machine's slot is free again.
        AcquireResult again = memory.backing("db").acquire("app", new Owner("next", UUID.randomUUID()), 4, Duration.ofSeconds(30),
            Duration.ofSeconds(1), 0, OptionalInt.of(0));
        assertThat(((AcquireResult.Acquired) again).lease().slot(), is(0));
    }

    @Test
    void eachMachineIsReleasedWithTheLastIssuedOfItsOwnGenerators() {
        CosIdRuntime runtime = CosIdRuntime.start(twoMachines().build(), options());
        clock.advance(Duration.ofMillis(100));
        runtime.generator("trace").generate();
        clock.advance(Duration.ofMillis(5_000));
        runtime.get("token", PartsIdGenerator.class).nextParts();
        runtime.close();
        // Named machines first, in reverse start order, the default one last.
        assertThat(events.stream().filter(event -> event.startsWith("released:")).toList(), contains("released:global-ids", "released:app"));
        AcquireResult service = memory.backing("db").acquire("app", new Owner("next", UUID.randomUUID()), 4, Duration.ofSeconds(30),
            Duration.ofSeconds(1), 0, OptionalInt.of(0));
        assertThat(((AcquireResult.Acquired) service).lease().floor(), is(T0 + 100));
        AcquireResult global = memory.backing("cache").acquire("global-ids", new Owner("next", UUID.randomUUID()), 4, Duration.ofSeconds(30),
            Duration.ofSeconds(1), 0, OptionalInt.of(0));
        assertThat(((AcquireResult.Acquired) global).lease().floor(), is(T0 + 5_100));
    }

    @Test
    void textOfEveryStrategy() {
        try (CosIdRuntime runtime = CosIdRuntime.start(twoMachines()
            .generator("order", segment("db", new CodecSpec.Prefix("O")))
            .generator("bill", GeneratorSpec.PeriodicSegment.builder("db", me.ahoo.cosid.segment.Period.YEAR, java.time.ZoneOffset.UTC).build())
            .generator("off", new GeneratorSpec.Disabled(segment("db")))
            .build(), options())) {
            assertThat(runtime.text("trace").get().matches("\\d+"), is(true));
            assertThat(runtime.text("order").get(), is("O1"));
            assertThat(runtime.text("token").get().isEmpty(), is(false));
            assertThat(runtime.text("bill").get(), startsWith(Integer.toString(Instant.ofEpochMilli(T0).atZone(java.time.ZoneOffset.UTC).getYear())));
            assertThrows(java.util.NoSuchElementException.class, () -> runtime.text("nope"));
            assertThrows(me.ahoo.cosid.GeneratorDisabledException.class, () -> runtime.text("off"));
            assertThat(GroupedId.class.isInstance(runtime.periodic("bill").generate()), is(true));
        }
    }
}
