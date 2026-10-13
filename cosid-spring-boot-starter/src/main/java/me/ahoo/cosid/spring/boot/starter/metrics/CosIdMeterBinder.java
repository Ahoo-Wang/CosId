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

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.event.CosIdEventListener;
import me.ahoo.cosid.event.CosIdEvents;
import me.ahoo.cosid.event.LeaseClaim;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.NamespacedInstanceId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.CountedSegmentId;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.snowflake.CountedSnowflakeId;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * The Micrometer meters of CosId: gauges read from the generators and machine id leases of this application context,
 * counters and timers fed by {@link CosIdEvents}.
 *
 * <p>The listener is registered when the bean is created and removed when the context closes. {@link CosIdEvents} is
 * JVM-wide: with an isolated provider ({@code cosid.provider.isolated=true}), segment events count only for the segment
 * stores of this context's generators and lease events only for this context's namespaces. Clock, sequence and call
 * timeout meters have nothing to tell contexts apart: they count the whole JVM.</p>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public class CosIdMeterBinder implements MeterBinder, CosIdEventListener, InitializingBean, SmartInitializingSingleton, DisposableBean {
    public static final String PREFIX = "cosid.";
    static final String TAG_NAME = "name";
    static final String TAG_SEGMENT = "segment";
    static final String TAG_NAMESPACE = "namespace";
    
    private final IdGeneratorProvider idGeneratorProvider;
    @Nullable
    private final MachineIdGuarder machineIdGuarder;
    private final boolean isolated;
    private final Set<String> ownSegments = ConcurrentHashMap.newKeySet();
    private final Set<String> ownNamespaces = ConcurrentHashMap.newKeySet();
    private final Set<String> leaseGauges = ConcurrentHashMap.newKeySet();
    @Nullable
    private volatile MeterRegistry registry;
    @Nullable
    private AutoCloseable registration;
    
    /**
     * Create the binder.
     *
     * @param idGeneratorProvider the provider of this context
     * @param machineIdGuarder    the guarder of this context, {@code null} without machine ids
     * @param isolated            whether the provider is isolated to this context
     * @param namespaces          the namespaces of this context, for an isolated provider
     */
    public CosIdMeterBinder(IdGeneratorProvider idGeneratorProvider, @Nullable MachineIdGuarder machineIdGuarder, boolean isolated,
                            Set<String> namespaces) {
        this.idGeneratorProvider = idGeneratorProvider;
        this.machineIdGuarder = machineIdGuarder;
        this.isolated = isolated;
        this.ownNamespaces.addAll(namespaces);
    }
    
    @Override
    public void afterPropertiesSet() {
        registration = CosIdEvents.register(this);
    }
    
    @Override
    public void destroy() throws Exception {
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        refresh();
    }
    
    @Override
    public void afterSingletonsInstantiated() {
        refresh();
    }
    
    /**
     * Register the gauges of the generators and leases there are now. Registering a meter again keeps the first one.
     */
    public void refresh() {
        MeterRegistry meterRegistry = registry;
        idGeneratorProvider.entries().forEach(entry -> {
            String name = entry.getKey();
            Object generator = entry.getValue();
            CountedSegmentId countedSegment = find(generator, CountedSegmentId.class);
            CountedSnowflakeId countedSnowflake = find(generator, CountedSnowflakeId.class);
            Object actual = Decorator.getActual(generator);
            if (actual instanceof SegmentChainId chain) {
                ownSegments.add(chain.getNamespacedName());
                if (meterRegistry != null) {
                    gauge(meterRegistry, "segment.remaining", chain, SegmentChainId::getRemaining, name, chain.getNamespacedName(),
                        "IDs left in the segments the generator already holds");
                    gauge(meterRegistry, "segment.prefetch.distance", chain, SegmentChainId::getPrefetchDistance, name,
                        chain.getNamespacedName(), "Segments the generator prefetches ahead");
                }
            } else if (actual instanceof DefaultSegmentId segment) {
                ownSegments.add(segment.getNamespacedName());
                if (meterRegistry != null) {
                    gauge(meterRegistry, "segment.remaining", segment, DefaultSegmentId::getRemaining, name, segment.getNamespacedName(),
                        "IDs left in the segments the generator already holds");
                }
            }
            if (meterRegistry != null && countedSegment != null) {
                generated(meterRegistry, name, countedSegment, CountedSegmentId::getGenerated);
            }
            if (meterRegistry != null && countedSnowflake != null) {
                generated(meterRegistry, name, countedSnowflake, CountedSnowflakeId::getGenerated);
            }
        });
        if (machineIdGuarder != null) {
            machineIdGuarder.getLeases().keySet().forEach(key -> leaseGauges(key.getNamespace()));
        }
    }
    
    @Nullable
    private static <T> T find(Object generator, Class<T> type) {
        Object current = generator;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current instanceof Decorator<?> decorator ? decorator.getActual() : null;
        }
        return null;
    }
    
    private static <T> void gauge(MeterRegistry registry, String name, T target, ToDoubleFunction<T> value, String generator,
                                  String segment, String description) {
        Gauge.builder(PREFIX + name, target, value)
            .tag(TAG_NAME, generator)
            .tag(TAG_SEGMENT, segment)
            .description(description)
            .register(registry);
    }
    
    private static <T> void generated(MeterRegistry registry, String generator, T counted, ToLongFunction<T> count) {
        FunctionCounter.builder(PREFIX + "generator.generated", counted, target -> count.applyAsLong(target))
            .tag(TAG_NAME, generator)
            .description("IDs the generator generated")
            .register(registry);
    }
    
    private void leaseGauges(String namespace) {
        MeterRegistry meterRegistry = registry;
        ownNamespaces.add(namespace);
        if (meterRegistry == null || machineIdGuarder == null || !leaseGauges.add(namespace)) {
            return;
        }
        MachineIdGuarder guarder = machineIdGuarder;
        TimeGauge.builder(PREFIX + "machine.lease.remaining", guarder, TimeUnit.MILLISECONDS, target -> remainingMillis(target, namespace))
            .tag(TAG_NAMESPACE, namespace)
            .description("Time left before the machine id lease expires; the Snowflake generators stop at zero")
            .register(meterRegistry);
        TimeGauge.builder(PREFIX + "machine.clock.skew", guarder, TimeUnit.MILLISECONDS, target -> clockSkewMillis(target, namespace))
            .tag(TAG_NAMESPACE, namespace)
            .description("How far this host's clock was ahead of the machine id store's at the last renewal")
            .register(meterRegistry);
    }
    
    @Nullable
    private static MachineIdLease lease(MachineIdGuarder guarder, String namespace) {
        for (Map.Entry<NamespacedInstanceId, MachineIdLease> entry : guarder.getLeases().entrySet()) {
            if (namespace.equals(entry.getKey().getNamespace())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    private static double remainingMillis(MachineIdGuarder guarder, String namespace) {
        MachineIdLease lease = lease(guarder, namespace);
        if (lease == null || lease.isLost() || lease.isReleased()) {
            return 0;
        }
        if (lease.getValidUntil() == Long.MAX_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, lease.getValidUntil() - System.currentTimeMillis());
    }
    
    private static double clockSkewMillis(MachineIdGuarder guarder, String namespace) {
        MachineIdLease lease = lease(guarder, namespace);
        return lease == null ? 0 : lease.getClockSkewMillis();
    }
    
    private boolean ownsSegment(String namespacedName) {
        return !isolated || ownSegments.contains(namespacedName);
    }
    
    private boolean ownsNamespace(String namespace) {
        return !isolated || ownNamespaces.contains(namespace);
    }
    
    private void count(String name, String description, String... tags) {
        MeterRegistry meterRegistry = registry;
        if (meterRegistry != null) {
            Counter.builder(PREFIX + name).tags(tags).description(description).register(meterRegistry).increment();
        }
    }
    
    private void time(String name, Duration duration, String description, String... tags) {
        MeterRegistry meterRegistry = registry;
        if (meterRegistry != null) {
            Timer.builder(PREFIX + name).tags(tags).description(description).register(meterRegistry).record(duration);
        }
    }
    
    @Override
    public void onClockBackwards(String generator, long lastTimestamp, long currentTimestamp) {
        count("clock.backwards", "Times a generator saw the clock move backwards", "generator", generator);
    }
    
    @Override
    public void onClockSyncWait(String generator, Duration waited) {
        time("clock.sync.wait", waited, "Waits for the clock to catch up after it moved backwards", "generator", generator);
    }
    
    @Override
    public void onSequenceExhaustedWait(Duration waited) {
        time("sequence.exhausted.wait", waited, "Waits for the next tick after a generator used up its sequence");
    }
    
    @Override
    public void onSegmentFetch(String namespacedName, Duration latency, @Nullable Throwable failure) {
        if (ownsSegment(namespacedName)) {
            time("segment.fetch", latency, "Segment fetches from the store", TAG_SEGMENT, namespacedName, "outcome",
                failure == null ? "success" : "failure");
        }
    }
    
    @Override
    public void onPrefetchExpansion(String namespacedName, int fromDistance, int toDistance) {
        if (ownsSegment(namespacedName)) {
            count("segment.prefetch.expansions", "Times a segment chain ran dry and grew its prefetch distance", TAG_SEGMENT, namespacedName);
        }
    }
    
    @Override
    public void onPrefetchFailure(String namespacedName, Throwable failure) {
        if (ownsSegment(namespacedName)) {
            count("segment.prefetch.failures", "Failed segment prefetches", TAG_SEGMENT, namespacedName);
        }
    }
    
    @Override
    public void onLeaseAcquired(String namespace, InstanceId instanceId, int machineId, LeaseClaim claim) {
        if (ownsNamespace(namespace)) {
            count("machine.lease.acquired", "Machine id leases taken, by how", TAG_NAMESPACE, namespace, "claim", claim.name().toLowerCase());
        }
    }
    
    @Override
    public void onLeaseRenewed(String namespace, InstanceId instanceId, int machineId, long clockSkewMillis) {
        if (ownsNamespace(namespace)) {
            leaseGauges(namespace);
        }
    }
    
    @Override
    public void onLeaseRenewFailed(String namespace, InstanceId instanceId, Throwable failure) {
        if (ownsNamespace(namespace)) {
            count("machine.lease.renew.failures", "Failed machine id lease renewals", TAG_NAMESPACE, namespace);
        }
    }
    
    @Override
    public void onLeaseLost(String namespace, InstanceId instanceId) {
        if (ownsNamespace(namespace)) {
            count("machine.lease.lost", "Machine id leases lost to another instance or expired", TAG_NAMESPACE, namespace);
        }
    }
    
    @Override
    public void onCallTimeout(String operation, Duration timeout) {
        count("call.timeouts", "Backend calls that timed out", "operation", operation);
    }
    
    @Override
    public void onCallRejected(String operation) {
        count("call.rejections", "Backend calls rejected: too many in flight", "operation", operation);
    }
    
    @Override
    public String toString() {
        return "CosIdMeterBinder{isolated=" + isolated + '}';
    }
}
