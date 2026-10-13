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

package me.ahoo.cosid.spring.boot.starter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.event.CosIdEvents;
import me.ahoo.cosid.event.LeaseClaim;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.segment.CountedSegmentId;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.snowflake.CountedSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class CosIdMeterBinderTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
    private final PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
    private CosIdMeterBinder binder;
    
    @AfterEach
    void close() throws Exception {
        if (binder != null) {
            binder.destroy();
        }
        executor.shutdown();
    }
    
    private CosIdMeterBinder bind(boolean isolated) {
        binder = new CosIdMeterBinder(provider, null, isolated, Set.of("mine"));
        binder.bindTo(registry);
        return binder;
    }
    
    @Test
    void gaugesOfSegmentGenerators() {
        SegmentChainId chain = new SegmentChainId(IdSegment.TIME_TO_LIVE_FOREVER, 2, 8, new AtomicIdSegmentDistributor(10), executor);
        DefaultSegmentId segment = new DefaultSegmentId(new AtomicIdSegmentDistributor(10));
        provider.set("order", chain);
        provider.set("user", segment);
        chain.generate();
        segment.generate();
        bind(false);
        
        assertThat(registry.get("cosid.segment.remaining").tag("name", "order").tag("segment", chain.getNamespacedName()).gauge().value())
            .isEqualTo(chain.getRemaining()).isPositive();
        assertThat(registry.get("cosid.segment.prefetch.distance").tag("name", "order").gauge().value())
            .isEqualTo(chain.getPrefetchDistance()).isBetween(2.0, 8.0);
        assertThat(registry.get("cosid.segment.remaining").tag("name", "user").gauge().value()).isEqualTo(9);
    }
    
    @Test
    void countOfGeneratedIds() {
        CountedSegmentId segment = new CountedSegmentId(new DefaultSegmentId(new AtomicIdSegmentDistributor(10)));
        CountedSnowflakeId snowflake = new CountedSnowflakeId(new MillisecondSnowflakeId(1));
        provider.set("order", segment);
        provider.set("snowflake", snowflake);
        bind(false);
        segment.generate();
        segment.generate();
        snowflake.generate();
        
        assertThat(registry.get("cosid.generator.generated").tag("name", "order").functionCounter().count()).isEqualTo(2);
        assertThat(registry.get("cosid.generator.generated").tag("name", "snowflake").functionCounter().count()).isEqualTo(1);
    }
    
    @Test
    void noGeneratedCountWithoutTheCountingDecorator() {
        provider.set("order", new DefaultSegmentId(new AtomicIdSegmentDistributor(10)));
        bind(false);
        assertThat(registry.find("cosid.generator.generated").meters()).isEmpty();
    }
    
    @Test
    void countersAndTimersFromEvents() {
        bind(false);
        CosIdEvents.listener().onClockBackwards("MillisecondSnowflakeId", 2, 1);
        CosIdEvents.listener().onClockSyncWait("MillisecondSnowflakeId", Duration.ofMillis(3));
        CosIdEvents.listener().onSequenceExhaustedWait(Duration.ofMillis(1));
        CosIdEvents.listener().onSegmentFetch("ns.order", Duration.ofMillis(5), null);
        CosIdEvents.listener().onSegmentFetch("ns.order", Duration.ofMillis(5), new IllegalStateException("down"));
        CosIdEvents.listener().onPrefetchExpansion("ns.order", 2, 4);
        CosIdEvents.listener().onPrefetchFailure("ns.order", new IllegalStateException("down"));
        CosIdEvents.listener().onLeaseAcquired("ns", InstanceId.of("i", false), 1, LeaseClaim.EXPIRED);
        CosIdEvents.listener().onLeaseRenewFailed("ns", InstanceId.of("i", false), new IllegalStateException("down"));
        CosIdEvents.listener().onLeaseLost("ns", InstanceId.of("i", false));
        CosIdEvents.listener().onCallTimeout("nextMaxId", Duration.ofSeconds(1));
        CosIdEvents.listener().onCallRejected("nextMaxId");
        
        assertThat(registry.get("cosid.clock.backwards").tag("type", "MillisecondSnowflakeId").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.clock.sync.wait").tag("type", "MillisecondSnowflakeId").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(3);
        assertThat(registry.get("cosid.sequence.exhausted.wait").timer().count()).isEqualTo(1);
        assertThat(registry.get("cosid.segment.fetch").tag("segment", "ns.order").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("cosid.segment.fetch").tag("segment", "ns.order").tag("outcome", "failure").timer().count()).isEqualTo(1);
        assertThat(registry.get("cosid.segment.prefetch.expansions").tag("segment", "ns.order").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.segment.prefetch.failures").tag("segment", "ns.order").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.machine.lease.acquired").tag("namespace", "ns").tag("claim", "expired").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.machine.lease.renew.failures").tag("namespace", "ns").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.machine.lease.lost").tag("namespace", "ns").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.call.timeouts").tag("operation", "nextMaxId").counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.call.rejections").tag("operation", "nextMaxId").counter().count()).isEqualTo(1);
    }
    
    @Test
    void nothingIsCountedOnceTheContextClosed() throws Exception {
        bind(false).destroy();
        CosIdEvents.listener().onLeaseLost("ns", InstanceId.of("i", false));
        assertThat(registry.find("cosid.machine.lease.lost").counter()).isNull();
    }
    
    @Test
    void aForeverLeaseHasNoEnd() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(
            new ManualMachineIdDistributor(7, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT), MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        guarder.register("forever", InstanceId.of("i", true), System.currentTimeMillis());
        assertThat(CosIdMeterBinder.remainingMillis(guarder, "forever")).isIn(Double.POSITIVE_INFINITY, (double) Long.MAX_VALUE);
        assertThat(CosIdMeterBinder.remainingMillis(guarder, "unknown")).isZero();
    }
    
    @Test
    void anIsolatedContextCountsOnlyItsOwnSegmentsAndInstances() {
        SegmentChainId chain = new SegmentChainId(IdSegment.TIME_TO_LIVE_FOREVER, 2, 8, new AtomicIdSegmentDistributor(10), executor);
        provider.set("order", chain);
        bind(true);
        CosIdEvents.listener().onPrefetchFailure(chain.getNamespacedName(), new IllegalStateException("mine"));
        CosIdEvents.listener().onPrefetchFailure("other.order", new IllegalStateException("another context"));
        // Two contexts on the same namespace: only this context's instance counts.
        CosIdEvents.listener().onLeaseLost("cosid", InstanceId.of("mine", false));
        CosIdEvents.listener().onLeaseLost("cosid", InstanceId.of("another-context", false));
        
        assertThat(registry.get("cosid.segment.prefetch.failures").counters()).hasSize(1);
        assertThat(registry.get("cosid.segment.prefetch.failures").tag("segment", chain.getNamespacedName()).counter().count()).isEqualTo(1);
        assertThat(registry.get("cosid.machine.lease.lost").tag("namespace", "cosid").counter().count()).isEqualTo(1);
    }
    
    @Test
    void everyBoundRegistryCountsAndTheMetersAreRemovedOnClose() throws Exception {
        SimpleMeterRegistry second = new SimpleMeterRegistry();
        provider.set("user", new DefaultSegmentId(new AtomicIdSegmentDistributor(10)));
        bind(false);
        binder.bindTo(second);
        binder.bindTo(second);
        CosIdEvents.listener().onLeaseLost("ns", InstanceId.of("i", false));
        
        assertThat(registry.get("cosid.machine.lease.lost").counter().count()).isEqualTo(1);
        assertThat(second.get("cosid.machine.lease.lost").counter().count()).isEqualTo(1);
        assertThat(second.get("cosid.segment.remaining").gauge()).isNotNull();
        
        binder.destroy();
        assertThat(registry.find("cosid.machine.lease.lost").counter()).isNull();
        assertThat(second.find("cosid.segment.remaining").gauge()).isNull();
        assertThat(second.find("cosid.sequence.exhausted.wait").timer()).isNull();
    }
    
    @Test
    void theListenerIsRegisteredOnTheFirstBinding() {
        binder = new CosIdMeterBinder(provider, null, false, Set.of());
        CosIdEvents.listener().onLeaseLost("ns", InstanceId.of("i", false));
        binder.bindTo(registry);
        CosIdEvents.listener().onLeaseLost("ns", InstanceId.of("i", false));
        assertThat(registry.get("cosid.machine.lease.lost").counter().count()).isEqualTo(1);
    }
    
    @Test
    void gaugesOfTheMachineIdLeases() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(
            new ManualMachineIdDistributor(7, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT), Duration.ofMinutes(5));
        guarder.register("orders", InstanceId.of("i", false), System.currentTimeMillis());
        binder = new CosIdMeterBinder(provider, guarder, false, Set.of());
        binder.bindTo(registry);
        
        assertThat(registry.get("cosid.machine.lease.remaining").tag("namespace", "orders").timeGauge().value(TimeUnit.MILLISECONDS)).isPositive();
        assertThat(registry.get("cosid.machine.clock.skew").tag("namespace", "orders").timeGauge().value(TimeUnit.MILLISECONDS)).isZero();
    }
}
