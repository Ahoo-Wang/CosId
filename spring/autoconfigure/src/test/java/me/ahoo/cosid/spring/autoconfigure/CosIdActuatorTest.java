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

package me.ahoo.cosid.spring.autoconfigure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.RangeUnavailableException;
import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.store.StoreTimeoutException;
import me.ahoo.cosid.store.tck.Fault;
import me.ahoo.cosid.store.tck.FaultInjector;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The {@code cosid} endpoint, the health indicator and the Micrometer metrics (11 §5).
 */
class CosIdActuatorTest {
    static ApplicationContextRunner runner() {
        return CosIdAutoConfigurationTest.withStores().withPropertyValues("management.endpoints.web.exposure.include=*");
    }

    @Test
    void theEndpointDescribesAndGenerates() {
        runner().withPropertyValues(CosIdAutoConfigurationTest.MIXED)
            .withPropertyValues("cosid.generators.off.strategy=segment", "cosid.generators.off.disabled=true").run(context -> {
                assertNull(context.getStartupFailure());
                CosIdEndpoint endpoint = context.getBean(CosIdEndpoint.class);
                Map<String, CosIdEndpoint.Generator> all = endpoint.describe();
                assertEquals(List.of("bill", "default", "off", "order", "token", "trace"), List.copyOf(all.keySet()));
                assertEquals("segment", endpoint.generator("order").strategy());
                assertEquals("other", endpoint.generator("order").store());
                assertNull(endpoint.generator("nope"));

                assertThat((Long) endpoint.generate("default", CosIdEndpoint.LONG), greaterThan(0L));
                long order = (Long) endpoint.generate("order", CosIdEndpoint.LONG);
                assertThat((Long) endpoint.generate("order", CosIdEndpoint.LONG), greaterThan(order));
                assertThat((String) endpoint.generate("order", CosIdEndpoint.STRING), startsWith("O"));
                assertThat((String) endpoint.generate("trace", CosIdEndpoint.STRING), containsString("-"));
                assertEquals(14, ((String) endpoint.generate("bill", CosIdEndpoint.STRING)).length());
                assertThat(((String) endpoint.generate("token", CosIdEndpoint.STRING)).length(), greaterThan(10));
                assertNull(endpoint.generate("nope", CosIdEndpoint.LONG));

                assertThat(assertThrows(InvalidEndpointRequestException.class, () -> endpoint.generate("token", CosIdEndpoint.LONG)).getMessage(),
                    containsString("issues no long ids"));
                assertThrows(InvalidEndpointRequestException.class, () -> endpoint.generate("bill", CosIdEndpoint.LONG));
                assertThrows(InvalidEndpointRequestException.class, () -> endpoint.generate("order", "hex"));
                assertThat(assertThrows(InvalidEndpointRequestException.class, () -> endpoint.generate("off", CosIdEndpoint.STRING)).getMessage(),
                    containsString("off"));
            });
    }

    @Test
    void theEndpointIsOnlyThereWhenExposed() {
        CosIdAutoConfigurationTest.withStores().withPropertyValues("cosid.generators.default.strategy=snowflake", "cosid.machine.static.slot=1")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertFalse(context.containsBean("cosIdEndpoint"));
            });
    }

    @Test
    void theHealthFollowsTheLease() {
        runner().withPropertyValues("cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem", "cosid.generators.default.strategy=snowflake")
            .run(context -> {
                assertNull(context.getStartupFailure());
                CosIdHealthIndicator indicator = context.getBean(CosIdHealthIndicator.class);
                Health health = indicator.health();
                assertEquals(Status.UP, health.getStatus());
                Map<String, Object> details = machine(health, "default");
                String namespace = context.getBean(CosIdRuntime.class).namespace();
                assertEquals(namespace, details.get("namespace"));
                assertEquals(0, details.get("slot"));
                assertThat((Long) details.get("windowRemainingMillis"), greaterThan(0L));
                assertThat((Long) details.get("expiresInMillis"), lessThanOrEqualTo(30_000L));
                assertEquals(true, details.get("valid"));
                assertNotNull(details.get("owner"));

                // The events of the machine's namespace; another namespace's do not touch it.
                me.ahoo.cosid.lease.SlotLease lease = RecordingListener.LEASE.toBuilder().namespace(namespace).build();
                indicator.onSlotRenewFailed(lease, new IllegalStateException("timeout"));
                indicator.onSlotRenewFailed(lease, new IllegalStateException("timeout"));
                indicator.onSlotLost(RecordingListener.LEASE);
                health = indicator.health();
                assertEquals(Status.UP, health.getStatus());
                assertEquals(2, machine(health, "default").get("renewFailures"));
                assertEquals("java.lang.IllegalStateException: timeout", machine(health, "default").get("lastRenewError"));
                indicator.onSlotRenewed(lease);
                assertEquals(0, machine(indicator.health(), "default").get("renewFailures"));

                indicator.onSlotLost(lease);
                assertEquals(Status.DOWN, indicator.health().getStatus());
                assertEquals(true, machine(indicator.health(), "default").get("lost"));
                indicator.onSlotAcquired(lease, "sticky");
                assertEquals(Status.UP, indicator.health().getStatus());
            });
    }

    @Test
    void eachMachineHasItsHealthAndMeters() {
        // A named machine through a customizer (its cosid.machines keys come later).
        runner().withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(RuntimeSpecCustomizer.class, () -> spec -> spec.toBuilder()
                .machine("global", me.ahoo.cosid.runtime.MachineSpec.Leased.builder("mem").namespace("global-ids").build())
                .generator("token", me.ahoo.cosid.runtime.GeneratorSpec.CosId80.builder().machine("global").build())
                .build())
            .withPropertyValues("cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem", "cosid.generators.default.strategy=snowflake")
            .run(context -> {
                assertNull(context.getStartupFailure());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertEquals(java.util.Set.of("default", "global"), runtime.machines());
                Health health = context.getBean(CosIdHealthIndicator.class).health();
                assertEquals(Status.UP, health.getStatus());
                assertEquals(runtime.namespace(), machine(health, "default").get("namespace"));
                assertEquals("global-ids", machine(health, "global").get("namespace"));
                assertEquals(true, machine(health, "global").get("valid"));

                MeterRegistry registry = context.getBean(MeterRegistry.class);
                assertEquals(1.0, registry.get("cosid.machine.lease.acquired").tag("machine", "global").tag("namespace", "global-ids").counter().count());
                assertEquals(1.0, registry.get("cosid.machine.lease.acquired").tag("machine", "default").counter().count());
                assertThat(registry.get("cosid.machine.lease.remaining").tag("machine", "global").timeGauge().value(), greaterThan(0.0));
                assertThat(registry.get("cosid.machine.lease.remaining").tag("machine", "default").timeGauge().value(), greaterThan(0.0));

                context.getBean(CosIdHealthIndicator.class).onSlotLost(runtime.slot("global").orElseThrow());
                assertEquals(Status.DOWN, context.getBean(CosIdHealthIndicator.class).health().getStatus());
            });
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> machine(Health health, String name) {
        return ((Map<String, Map<String, Object>>) health.getDetails().get("machines")).get(name);
    }

    @Test
    void theEndpointGeneratesLongIdsOfACustomGenerator() {
        IdGenerator legacy = new java.util.concurrent.atomic.AtomicLong(41)::incrementAndGet;
        runner().withBean(RuntimeSpecCustomizer.class, () -> spec -> spec.toBuilder()
            .generator("legacy", me.ahoo.cosid.runtime.GeneratorSpec.Instance.builder(legacy).build()).build()).run(context -> {
                assertNull(context.getStartupFailure());
                CosIdEndpoint endpoint = context.getBean(CosIdEndpoint.class);
                assertEquals(CosIdSpecs.CUSTOM, endpoint.generator("legacy").strategy());
                assertEquals(42L, endpoint.generate("legacy", CosIdEndpoint.LONG));
                assertEquals("43", endpoint.generate("legacy", CosIdEndpoint.STRING));
                assertEquals(44L, context.getBean("legacy", IdGenerator.class).generate());
            });
    }

    @Test
    void theHealthOfAStaticSlotAndOfNoSlot() {
        runner().withPropertyValues("cosid.machine.static.slot=5", "cosid.generators.default.strategy=snowflake").run(context -> {
            Health health = context.getBean(CosIdHealthIndicator.class).health();
            assertEquals(Status.UP, health.getStatus());
            assertEquals(5, machine(health, "default").get("slot"));
            assertFalse(machine(health, "default").containsKey("owner"), "a static slot has no lease");
        });
        runner().withPropertyValues("cosid.stores.mem.type=memory", "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem")
            .run(context -> {
                Health health = context.getBean(CosIdHealthIndicator.class).health();
                assertEquals(Status.UP, health.getStatus());
                assertTrue(health.getDetails().isEmpty(), "no machine started");
            });
        runner().withPropertyValues("management.health.cosid.enabled=false", "cosid.generators.default.strategy=snowflake",
            "cosid.machine.static.slot=1").run(context -> assertFalse(context.containsBean("cosidHealthIndicator")));
    }

    @Test
    void theHealthWithoutARuntimeIsUnknown() {
        @SuppressWarnings("unchecked")
        ObjectProvider<CosIdRuntime> none = (ObjectProvider<CosIdRuntime>) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {ObjectProvider.class}, (proxy, method, args) -> null);
        assertEquals(Status.UNKNOWN, new CosIdHealthIndicator(none).health().getStatus());
    }

    @Test
    void theMetricsCoverSegmentsLeasesAndEvents() {
        runner().withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("cosid.stores.mem.type=memory", "cosid.machine.leased.store=mem", "cosid.generators.default.strategy=snowflake",
                "cosid.generators.order.strategy=segment", "cosid.generators.order.store=mem", "cosid.metrics.generated.enabled=true",
                "cosid.generators.token.strategy=cosid80", "cosid.generators.off.strategy=segment", "cosid.generators.off.disabled=true")
            .run(context -> {
                assertNull(context.getStartupFailure());
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                IdGenerator order = context.getBean("order", IdGenerator.class);
                for (int i = 0; i < 5; i++) {
                    order.generate();
                }
                context.getBean(IdFiller.class).fill(new CosIdAutoConfigurationTest.Entity());
                context.getBean(CosIdRuntime.class).stringGenerator("order").generate();
                context.getBean("default", IdGenerator.class).generate();
                context.getBean("token", PartsIdGenerator.class).nextParts();
                assertEquals(7.0, registry.get("cosid.generator.generated").tag("name", "order").functionCounter().count());
                assertEquals(1.0, registry.get("cosid.generator.generated").tag("name", "default").functionCounter().count());
                assertEquals(1.0, registry.get("cosid.generator.generated").tag("name", "token").functionCounter().count());
                assertTrue(registry.find("cosid.generator.generated").tag("name", "off").functionCounters().isEmpty());
                assertThat(registry.get("cosid.segment.held").tag("name", "order").gauge().value(), greaterThan(0.0));
                assertThat(registry.get("cosid.segment.batch").tag("name", "order").gauge().value(), greaterThan(0.0));
                assertTrue(registry.find("cosid.segment.held").tag("name", "off").gauges().isEmpty());
                assertThat(registry.get("cosid.segment.reserve").tag("name", "order").timer().count(), greaterThan(0L));
                assertEquals(1.0, registry.get("cosid.machine.lease.acquired").tag("reason", "lowest").counter().count());
                assertThat(registry.get("cosid.machine.lease.remaining").timeGauge().value(), greaterThan(0.0));
                assertThat(registry.get("cosid.machine.window.remaining").timeGauge().value(), greaterThan(0.0));

                CosIdMeterListener listener = context.getBean(CosIdMeterListener.class);
                RecordingListener.fire(listener);
                assertEquals(1.0, registry.get("cosid.segment.refill.failures").tag("error", "IllegalStateException").counter().count());
                assertEquals(1.0, registry.get("cosid.segment.batch.resizes").tag("direction", "up").counter().count());
                assertEquals(1.0, registry.get("cosid.segment.batch.resizes").tag("direction", "down").counter().count());
                assertEquals(1.0, registry.get("cosid.sequence.exhausted").counter().count());
                assertEquals(3.0, registry.get("cosid.clock.backwards").tag("action", "borrow").summary().totalAmount());
                assertEquals(1.0, registry.get("cosid.machine.window.exceeded").counter().count());
                assertEquals(1.0, registry.get("cosid.machine.lease.renewed").counter().count());
                assertEquals(1.0, registry.get("cosid.machine.lease.renew.failures").counter().count());
                assertEquals(1.0, registry.get("cosid.machine.lease.lost").counter().count());
                assertEquals(1.0, registry.get("cosid.machine.lease.released").counter().count());
                // The skew gauge is the one of the machine's namespace.
                listener.onClockSkew(context.getBean(CosIdRuntime.class).namespace(), 7);
                assertEquals(7.0, registry.get("cosid.machine.clock.skew").tag("machine", "default").timeGauge()
                    .value(java.util.concurrent.TimeUnit.MILLISECONDS));
                assertEquals(1.0, registry.get("cosid.machine.lease.lost").tag("machine", "default").counter().count());
                assertEquals(1.0, registry.get("cosid.machine.owner.duplicates").counter().count());
                assertEquals(1.0, registry.get("cosid.store.timeouts").tag("operation", "reserve").counter().count());
                long names = registry.getMeters().stream().map(meter -> meter.getId().getName()).filter(name -> name.startsWith("cosid."))
                    .distinct().count();
                assertEquals(19, names, "the meters of CosIdMeterListener");
            });
    }

    @Test
    void theStoreTimeoutsCountTimeoutsOfRealStoreCalls() {
        // Not RecordingListener.fire: a reserve the store times out (the TCK's fault injection), through the runtime.
        MemoryStores stores = new MemoryStores();
        stores.faults = new FaultInjector().once(FaultInjector.Operation.RESERVE,
            new Fault.Fail(() -> new StoreTimeoutException("the store did not answer in time")));
        CosIdAutoConfigurationTest.RUNNER.withBean(MemoryStores.class, () -> stores).withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("cosid.stores.mem.type=memory", "cosid.stores.mem.timeout=100ms", "cosid.generators.order.strategy=segment",
                "cosid.generators.order.store=mem", "cosid.generators.order.lookahead=0s")
            .run(context -> {
                assertNull(context.getStartupFailure());
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                IdGenerator order = context.getBean("order", IdGenerator.class);
                assertThrows(RangeUnavailableException.class, order::generate);
                assertEquals(1.0, registry.get("cosid.store.timeouts").tag("store", "mem").tag("operation", "reserve").counter().count());
            });
    }

    @Test
    void theMetricsNeedARegistryAndCountOnlyWhenAsked() {
        CosIdAutoConfigurationTest.withStores().withPropertyValues("cosid.machine.static.slot=1", "cosid.generators.default.strategy=snowflake")
            .run(context -> {
                assertFalse(context.containsBean("cosIdMeterListener"));
                assertTrue(context.getBean(CosIdRuntime.class).generated("default").isEmpty());
            });
        runner().withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("cosid.machine.static.slot=1", "cosid.generators.default.strategy=snowflake").run(context -> {
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                assertTrue(registry.find("cosid.generator.generated").functionCounters().isEmpty());
                assertTrue(registry.find("cosid.machine.lease.remaining").timeGauges().isEmpty(), "a static slot has no lease");
                assertEquals(0, Stream.of(registry.getMeters().toArray()).filter(FunctionCounter.class::isInstance).count());
            });
    }
}
