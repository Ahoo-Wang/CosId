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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.store.tck.ManualClock;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Startup rollback and the close order (07-runtime-and-spring.md §1, 05-machine-lease.md §4.3).
 */
class RuntimeLifecycleTest {
    static final long T0 = 1_800_000_000_000L;

    final List<String> events = new CopyOnWriteArrayList<>();
    final ManualClock clock = new ManualClock(T0);
    final TestStores memory = new TestStores("memory", clock, events);
    final List<TrackedExecutor> executors = new CopyOnWriteArrayList<>();
    final GeneratorListener listener = new GeneratorListener() {
        @Override
        public void onSlotAcquired(SlotLease lease, String reason) {
            events.add("acquired:" + lease.slot());
        }

        @Override
        public void onSlotReleased(SlotLease lease) {
            events.add("released:" + lease.slot());
        }
    };

    RuntimeOptions options() {
        RuntimeOptions defaults = RuntimeOptions.defaults();
        return new RuntimeOptions(clock, Optional.empty(), listener, List.of(memory), Map.of(), () -> "host",
            OptionalInt.empty(), defaults.codecs());
    }

    CosIdRuntime start(RuntimeSpec spec) {
        return CosIdRuntime.start(spec, options(), namespace -> {
            TrackedExecutor executor = new TrackedExecutor(events);
            executors.add(executor);
            return executor;
        });
    }

    static MachineSpec.Leased leased(String store) {
        return new MachineSpec.Leased(store, 2, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty(), Optional.empty());
    }

    RuntimeSpec mixed() {
        return new RuntimeSpec("app", Map.of("a", store("memory"), "b", store("memory"), "c", store("memory")),
            Optional.of(leased("b")),
            Map.of("ms", snowflake(Layout.SNOWFLAKE_MS), "s", snowflake(Layout.SNOWFLAKE_S), "order", segment("c"), "x", segment("a")));
    }

    void assertExecutorsTerminated() {
        assertThat(executors.size(), is(1));
        assertThat(executors.getFirst().isTerminated(), is(true));
        assertThat(executors.getFirst().running.get(), is(0));
    }

    /** The slot is free again: a stranger takes the lowest slot. */
    void assertSlotFree(int slot) {
        AcquireResult result = memory.backing("b").acquire("app", new Owner("stranger", UUID.randomUUID()), 4, Duration.ofSeconds(30),
            Duration.ofSeconds(1), OptionalInt.of(slot));
        assertThat(result, instanceOf(AcquireResult.Acquired.class));
        assertThat(((AcquireResult.Acquired) result).lease().slot(), is(slot));
    }

    @Test
    void closesInReverseOrderAndReleasesWithTheExactLargestLastIssued() {
        CosIdRuntime runtime = start(mixed());
        TimeBasedGenerator ms = runtime.get("ms", TimeBasedGenerator.class);
        TimeBasedGenerator seconds = runtime.get("s", TimeBasedGenerator.class);
        clock.advance(Duration.ofMillis(1234));
        ms.generate();
        // The seconds layout issues the second that starts at T0 + 1000.
        seconds.generate();
        runtime.generator("order").generate();
        int slot = runtime.slot().orElseThrow().slot();
        runtime.close();

        assertThat(events, contains("open:a", "open:b", "open:c", "acquired:" + slot, "released:" + slot,
            "close:c", "close:b", "close:a", "executor:shutdown"));
        assertExecutorsTerminated();
        assertThrows(IllegalStateException.class, ms::generate);
        assertThrows(IllegalStateException.class, () -> runtime.generator("order").generate());
        assertThat(runtime.slot(), is(Optional.empty()));

        // The next holder of the slot starts right after the largest timestamp issued: T0 + 1234, not the ceiling.
        AcquireResult next = memory.backing("b").acquire("app", new Owner("next", UUID.randomUUID()), 4, Duration.ofSeconds(30),
            Duration.ofSeconds(1), OptionalInt.of(slot));
        assertThat(((AcquireResult.Acquired) next).lease().floor(), is(T0 + 1234));

        // Closing again does nothing.
        runtime.close();
        assertThat(events.size(), is(9));
    }

    @Test
    void releaseWithoutAnyIdKeepsTheFloor() {
        CosIdRuntime runtime = start(mixed());
        int slot = runtime.slot().orElseThrow().slot();
        runtime.close();
        AcquireResult next = memory.backing("b").acquire("app", new Owner("next", UUID.randomUUID()), 4, Duration.ofSeconds(30),
            Duration.ofSeconds(1), OptionalInt.of(slot));
        assertThat(((AcquireResult.Acquired) next).lease().floor(), is(0L));
    }

    @Test
    void rollsBackWhenAStoreFailsToOpen() {
        memory.failOpen.add("b");
        CosIdException failed = assertThrows(CosIdException.class, () -> start(mixed()));
        assertThat(failed.getMessage(), containsString("CosIdRuntime [app] failed to start: cannot open b"));
        assertThat(events, contains("open:a", "openFailed:b", "close:a", "executor:shutdown"));
        assertExecutorsTerminated();
    }

    @Test
    void rollsBackWhenTheSlotIsNotAcquired() {
        memory.noSlots.add("b");
        assertThrows(CosIdException.class, () -> start(mixed()));
        assertThat(events, contains("open:a", "open:b", "open:c", "close:c", "close:b", "close:a", "executor:shutdown"));
        assertExecutorsTerminated();
    }

    @Test
    void rollsBackWhenEverySlotIsTaken() {
        for (int i = 0; i < 4; i++) {
            memory.backing("b").acquire("app", new Owner("other-" + i, UUID.randomUUID()), 4, Duration.ofSeconds(30),
                Duration.ofSeconds(1), OptionalInt.empty());
        }
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("b", store("memory")),
            Optional.of(new MachineSpec.Leased("b", 2, Duration.ofMillis(200), Duration.ZERO, OptionalInt.empty(), Optional.empty())),
            Map.of("ms", snowflake(Layout.SNOWFLAKE_MS)));
        assertThrows(CosIdException.class, () -> start(spec));
        assertThat(events, contains("open:b", "close:b", "executor:shutdown"));
        assertExecutorsTerminated();
    }

    @Test
    void rollsBackWhenAGeneratorFails() {
        memory.noRanges.add("c");
        CosIdException failed = assertThrows(CosIdException.class, () -> start(mixed()));
        assertThat(failed.getCause(), instanceOf(UnsupportedOperationException.class));
        int slot = Integer.parseInt(events.get(3).substring("acquired:".length()));
        assertThat(events, contains("open:a", "open:b", "open:c", "acquired:" + slot, "released:" + slot,
            "close:c", "close:b", "close:a", "executor:shutdown"));
        assertExecutorsTerminated();
        assertSlotFree(slot);
    }

    @Test
    void rollsBackWhenTheExecutorRejectsTheRenewals() {
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("b", store("memory")), Optional.of(leased("b")),
            Map.of("ms", snowflake(Layout.SNOWFLAKE_MS)));
        CosIdException failed = assertThrows(CosIdException.class, () -> CosIdRuntime.start(spec, options(), namespace -> {
            TrackedExecutor executor = new TrackedExecutor(events);
            executor.reject = true;
            executors.add(executor);
            return executor;
        }));
        assertThat(failed.getMessage(), containsString("rejected the renewals"));
        assertThat(events.getLast(), is("executor:shutdown"));
        assertThat(events.contains("close:b"), is(true));
        assertExecutorsTerminated();
        assertSlotFree(0);
    }

    @Test
    void aFailingCloseStepDoesNotStopTheOthers() {
        memory.failClose.add("b");
        CosIdRuntime runtime = start(mixed());
        CosIdException failed = assertThrows(CosIdException.class, runtime::close);
        assertThat(failed.getMessage(), containsString("closed with 1 failure(s)"));
        assertThat(failed.getCause().getMessage(), is("cannot close b"));
        assertThat(events.subList(events.size() - 4, events.size()), contains("close:c", "close:b", "close:a", "executor:shutdown"));
        assertExecutorsTerminated();
    }

    @Test
    void aCallerExecutorIsNotClosed() {
        TrackedExecutor own = new TrackedExecutor(events);
        RuntimeOptions base = options();
        RuntimeOptions withExecutor = new RuntimeOptions(base.time(), Optional.of(own), base.listener(), base.storeProviders(),
            base.clients(), base.hostResolver(), base.port(), base.codecs());
        CosIdRuntime runtime = CosIdRuntime.start(mixed(), withExecutor, namespace -> {
            throw new AssertionError("the runtime must not create an executor");
        });
        runtime.close();
        assertThat(own.isShutdown(), is(false));
        assertThat(events.contains("executor:shutdown"), is(false));
        own.shutdownNow();
    }
}
