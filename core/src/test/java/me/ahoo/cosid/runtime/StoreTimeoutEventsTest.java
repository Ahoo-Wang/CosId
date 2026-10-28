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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.RangeUnavailableException;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.store.StoreTimeoutException;
import me.ahoo.cosid.store.tck.Fault;
import me.ahoo.cosid.store.tck.FaultInjector;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * {@link GeneratorListener#onStoreTimeout} from real flows (07 §2): a store call failing with its native timeout
 * ({@link StoreTimeoutException}, injected with the TCK's fault injection) and a caller waiting for a refill longer
 * than the reserve timeout.
 */
class StoreTimeoutEventsTest {
    /** No prefetching, so that each refill is one the caller waits for. */
    private static final BatchPolicy WAITING = BatchPolicy.builder()
        .step(10)
        .initialOffset(0)
        .lookahead(Duration.ZERO)
        .maxBatch(10)
        .reserveTimeout(Duration.ofMillis(200))
        .build();

    private final List<String> timeouts = new CopyOnWriteArrayList<>();
    private final List<String> renewFailures = new CopyOnWriteArrayList<>();
    private final GeneratorListener listener = new GeneratorListener() {
        @Override
        public void onStoreTimeout(String store, String operation) {
            timeouts.add(store + ":" + operation);
        }

        @Override
        public void onSlotRenewFailed(SlotLease lease, Throwable error) {
            renewFailures.add(error.toString());
        }
    };

    private static StoreTimeoutException timedOut() {
        return new StoreTimeoutException("the store did not answer in time");
    }

    private CosIdRuntime start(TestStores stores, Optional<MachineSpec> machine, Map<String, GeneratorSpec> generators) {
        RuntimeSpec.Builder spec = RuntimeSpec.builder("app").store("db", StoreSpec.builder("mem").timeout(Duration.ofMillis(100)).build());
        machine.ifPresent(spec::machine);
        generators.forEach(spec::generator);
        return CosIdRuntime.start(spec.build(), CosIdRuntimeTest.options(TimeSource.SYSTEM, listener, stores));
    }

    @Test
    void aReserveThatTimesOutInTheStoreIsReported() {
        TestStores stores = new TestStores("mem");
        stores.faults.put("db", new FaultInjector().once(FaultInjector.Operation.RESERVE, new Fault.Fail(StoreTimeoutEventsTest::timedOut)));
        try (CosIdRuntime runtime = start(stores, Optional.empty(), Map.of("order", GeneratorSpec.Segment.builder("db")
            .batch(WAITING)
            .codec(List.of())
            .build()))) {
            RangeUnavailableException e = assertThrows(RangeUnavailableException.class, () -> runtime.generator("order").generate());
            assertThat(StoreTimeoutException.isTimeout(e), is(true));
            assertThat(timeouts, contains("db:reserve"));
        }
    }

    @Test
    void anotherStoreFailureIsNoTimeout() {
        TestStores stores = new TestStores("mem");
        stores.faults.put("db", new FaultInjector().once(FaultInjector.Operation.RESERVE, new Fault.Fail(() -> new CosIdException("refused"))));
        try (CosIdRuntime runtime = start(stores, Optional.empty(), Map.of("order", GeneratorSpec.Segment.builder("db")
            .batch(WAITING)
            .codec(List.of())
            .build()))) {
            assertThrows(RangeUnavailableException.class, () -> runtime.generator("order").generate());
            assertThat(timeouts.isEmpty(), is(true));
        }
    }

    @Test
    void aRefillWaitLongerThanTheReserveTimeoutIsReportedOncePerRefill() throws Exception {
        TestStores stores = new TestStores("mem");
        stores.faults.put("db", new FaultInjector().once(FaultInjector.Operation.RESERVE, new Fault.Delay(Duration.ofSeconds(1))));
        try (CosIdRuntime runtime = start(stores, Optional.empty(), Map.of("order", GeneratorSpec.Segment.builder("db")
            .batch(WAITING)
            .codec(List.of())
            .build()))) {
            IdGenerator order = runtime.generator("order");
            // The first caller makes the slow reservation itself; the later ones wait for it, and two of them give up
            // on that one refill: one event.
            CompletableFuture<Long> first = CompletableFuture.supplyAsync(order::generate);
            Thread.sleep(100);
            assertThrows(RangeUnavailableException.class, order::generate);
            assertThrows(RangeUnavailableException.class, order::generate);
            assertThat(first.get(10, TimeUnit.SECONDS), is(0L));
            assertThat(timeouts, contains("db:reserve-wait"));
        }
    }

    @Test
    void aPeriodicRefillWaitIsReportedWithItsStore() throws Exception {
        TestStores stores = new TestStores("mem");
        stores.faults.put("db", new FaultInjector().once(FaultInjector.Operation.RESERVE, new Fault.Delay(Duration.ofSeconds(1))));
        GeneratorSpec bill = GeneratorSpec.PeriodicSegment.builder("db", Period.YEAR, ZoneOffset.UTC)
            .batch(WAITING)
            .periodic(PeriodicPolicy.DEFAULT)
            .codec(new GroupedCodecSpec.DatePrefix("-", List.of()))
            .build();
        try (CosIdRuntime runtime = start(stores, Optional.empty(), Map.of("bill", bill))) {
            GroupedIdGenerator generator = runtime.get("bill", GroupedIdGenerator.class);
            CompletableFuture<GroupedId> first = CompletableFuture.supplyAsync(generator::generate);
            Thread.sleep(100);
            assertThrows(RangeUnavailableException.class, generator::generate);
            assertThat(first.get(10, TimeUnit.SECONDS).value(), is(0L));
            assertThat(timeouts, contains("db:reserve-wait"));
        }
    }

    @Test
    void anAcquireAndARenewThatTimeOutAreReported() throws InterruptedException {
        TestStores stores = new TestStores("mem");
        stores.faults.put("db", new FaultInjector()
            .once(FaultInjector.Operation.ACQUIRE, new Fault.Fail(StoreTimeoutEventsTest::timedOut))
            .always(FaultInjector.Operation.RENEW, new Fault.Fail(StoreTimeoutEventsTest::timedOut)));
        // A short ttl renews every ttl/3.
        MachineSpec leased = MachineSpec.Leased.builder("db").bits(10).ttl(Duration.ofMillis(600)).tolerance(Duration.ofMillis(100)).build();
        try (CosIdRuntime runtime = start(stores, Optional.of(leased),
            Map.of("default", GeneratorSpec.Snowflake.builder()
                .layout(Layout.SNOWFLAKE_MS)
                .regression(ClockRegressionPolicy.DEFAULT)
                .codec(List.of())
                .build()))) {
            assertThat(runtime.slot().isPresent(), is(true));
            assertThat(timeouts, hasItem("db:acquire"));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!timeouts.contains("db:renew") && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(timeouts, hasItem("db:renew"));
            assertThat(renewFailures, everyItem(org.hamcrest.Matchers.containsString("did not answer in time")));
            stores.faults.get("db").clear();
        }
    }
}
