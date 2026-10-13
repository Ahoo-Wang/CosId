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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * The Micrometer meters of CosId: gauges read from the generators and machine id leases of this application context,
 * counters and timers fed by {@link CosIdEvents}.
 *
 * <p>The listener is registered on the first {@link #bindTo(MeterRegistry)} and removed when the context closes, with
 * the meters it registered. Events only increment or record meters looked up once per tag set: some events fire under
 * a generator's lock.</p>
 *
 * <p>{@link CosIdEvents} is JVM-wide. With an isolated provider ({@code cosid.provider.isolated=true}), lease events
 * count only for this context's instances, and segment events only for the segment stores of this context's
 * generators: two contexts with generators on the same store both count its events. Clock, sequence and call
 * timeout meters have nothing to tell contexts apart and count the whole JVM. Generators added to the provider after
 * startup get gauges, and count in isolated mode, after the next {@link #refresh()}.</p>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public class CosIdMeterBinder implements MeterBinder, CosIdEventListener, SmartInitializingSingleton, DisposableBean {
    public static final String PREFIX = "cosid.";
    static final String TAG_NAME = "name";
    static final String TAG_SEGMENT = "segment";
    static final String TAG_NAMESPACE = "namespace";
    static final String TAG_TYPE = "type";
    
    private final IdGeneratorProvider idGeneratorProvider;
    @Nullable
    private final MachineIdGuarder machineIdGuarder;
    private final boolean isolated;
    private final Set<String> ownSegments = ConcurrentHashMap.newKeySet();
    private final Set<String> ownInstances = ConcurrentHashMap.newKeySet();
    private final List<Meters> registries = new CopyOnWriteArrayList<>();
    @Nullable
    private volatile AutoCloseable registration;
    
    /**
     * Create the binder.
     *
     * @param idGeneratorProvider the provider of this context
     * @param machineIdGuarder    the guarder of this context, {@code null} without machine ids
     * @param isolated            whether the provider is isolated to this context
     * @param instanceIds         the instance ids of this context, for an isolated provider
     */
    public CosIdMeterBinder(IdGeneratorProvider idGeneratorProvider, @Nullable MachineIdGuarder machineIdGuarder, boolean isolated,
                            Set<String> instanceIds) {
        this.idGeneratorProvider = idGeneratorProvider;
        this.machineIdGuarder = machineIdGuarder;
        this.isolated = isolated;
        this.ownInstances.addAll(instanceIds);
    }
    
    @Override
    public synchronized void bindTo(MeterRegistry registry) {
        for (Meters meters : registries) {
            if (meters.registry == registry) {
                return;
            }
        }
        Meters meters = new Meters(registry);
        registries.add(meters);
        refresh(meters);
        if (registration == null) {
            registration = CosIdEvents.register(this);
        }
    }
    
    @Override
    public void afterSingletonsInstantiated() {
        refresh();
    }
    
    @Override
    public synchronized void destroy() throws Exception {
        AutoCloseable current = registration;
        registration = null;
        if (current != null) {
            current.close();
        }
        for (Meters meters : registries) {
            meters.removeAll();
        }
        registries.clear();
    }
    
    /**
     * Register the gauges of the generators and leases there are now, in every bound registry.
     */
    public void refresh() {
        for (Meters meters : registries) {
            refresh(meters);
        }
    }
    
    private void refresh(Meters meters) {
        idGeneratorProvider.entries().forEach(entry -> {
            String name = entry.getKey();
            Object generator = entry.getValue();
            Object actual = Decorator.getActual(generator);
            if (actual instanceof SegmentChainId chain) {
                ownSegments.add(chain.getNamespacedName());
                meters.gauge("segment.remaining", chain, SegmentChainId::getRemaining, name, chain.getNamespacedName(),
                    "IDs left in the segments the generator already holds");
                meters.gauge("segment.prefetch.distance", chain, SegmentChainId::getPrefetchDistance, name, chain.getNamespacedName(),
                    "Segments the generator prefetches ahead");
            } else if (actual instanceof DefaultSegmentId segment) {
                ownSegments.add(segment.getNamespacedName());
                meters.gauge("segment.remaining", segment, DefaultSegmentId::getRemaining, name, segment.getNamespacedName(),
                    "IDs left in the segments the generator already holds");
            }
            CountedSegmentId countedSegment = find(generator, CountedSegmentId.class);
            if (countedSegment != null) {
                meters.generated(name, countedSegment, CountedSegmentId::getGenerated);
            }
            CountedSnowflakeId countedSnowflake = find(generator, CountedSnowflakeId.class);
            if (countedSnowflake != null) {
                meters.generated(name, countedSnowflake, CountedSnowflakeId::getGenerated);
            }
        });
        if (machineIdGuarder != null) {
            for (NamespacedInstanceId key : machineIdGuarder.getLeases().keySet()) {
                ownInstances.add(key.getInstanceId().getInstanceId());
                meters.leaseGauges(machineIdGuarder, key.getNamespace());
            }
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
    
    @Nullable
    private static MachineIdLease lease(MachineIdGuarder guarder, String namespace) {
        for (Map.Entry<NamespacedInstanceId, MachineIdLease> entry : guarder.getLeases().entrySet()) {
            if (namespace.equals(entry.getKey().getNamespace())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Milliseconds left on the lease of the namespace: 0 without a valid lease, {@code +Inf} for a lease that never
     * expires ({@code MachineIdLease.FOREVER}, the guarder off).
     */
    static double remainingMillis(MachineIdGuarder guarder, String namespace) {
        MachineIdLease lease = lease(guarder, namespace);
        if (lease == null || lease.isLost() || lease.isReleased()) {
            return 0;
        }
        if (lease.getValidUntil() == Long.MAX_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, lease.getValidUntil() - System.currentTimeMillis());
    }
    
    static double clockSkewMillis(MachineIdGuarder guarder, String namespace) {
        MachineIdLease lease = lease(guarder, namespace);
        return lease == null ? 0 : lease.getClockSkewMillis();
    }
    
    private boolean ownsSegment(String namespacedName) {
        return !isolated || ownSegments.contains(namespacedName);
    }
    
    private boolean ownsInstance(InstanceId instanceId) {
        return !isolated || ownInstances.contains(instanceId.getInstanceId());
    }
    
    @Override
    public void onClockBackwards(String generator, long lastTimestamp, long currentTimestamp) {
        for (Meters meters : registries) {
            meters.counter("clock.backwards", TAG_TYPE, generator, "Times a generator saw the clock move backwards").increment();
        }
    }
    
    @Override
    public void onClockSyncWait(String generator, Duration waited) {
        for (Meters meters : registries) {
            meters.timer("clock.sync.wait", TAG_TYPE, generator, "", "", "Waits for the clock to catch up after it moved backwards").record(waited);
        }
    }
    
    @Override
    public void onSequenceExhaustedWait(Duration waited) {
        for (Meters meters : registries) {
            meters.sequenceExhaustedWait.record(waited);
        }
    }
    
    @Override
    public void onSegmentFetch(String namespacedName, Duration latency, @Nullable Throwable failure) {
        if (!ownsSegment(namespacedName)) {
            return;
        }
        String outcome = failure == null ? "success" : "failure";
        for (Meters meters : registries) {
            meters.timer("segment.fetch", TAG_SEGMENT, namespacedName, "outcome", outcome, "Segment fetches from the store").record(latency);
        }
    }
    
    @Override
    public void onPrefetchExpansion(String namespacedName, int fromDistance, int toDistance) {
        if (!ownsSegment(namespacedName)) {
            return;
        }
        for (Meters meters : registries) {
            meters.counter("segment.prefetch.expansions", TAG_SEGMENT, namespacedName, "Times a segment chain ran dry and grew its prefetch distance")
                .increment();
        }
    }
    
    @Override
    public void onPrefetchFailure(String namespacedName, Throwable failure) {
        if (!ownsSegment(namespacedName)) {
            return;
        }
        for (Meters meters : registries) {
            meters.counter("segment.prefetch.failures", TAG_SEGMENT, namespacedName, "Failed segment prefetches").increment();
        }
    }
    
    @Override
    public void onLeaseAcquired(String namespace, InstanceId instanceId, int machineId, LeaseClaim claim) {
        if (!ownsInstance(instanceId)) {
            return;
        }
        String claimTag = claim.name().toLowerCase(Locale.ROOT);
        for (Meters meters : registries) {
            meters.counter("machine.lease.acquired", TAG_NAMESPACE, namespace, "claim", claimTag, "Machine id leases taken, by how").increment();
        }
    }
    
    @Override
    public void onLeaseRenewed(String namespace, InstanceId instanceId, int machineId, long clockSkewMillis) {
        if (!ownsInstance(instanceId) || machineIdGuarder == null) {
            return;
        }
        for (Meters meters : registries) {
            meters.leaseGauges(machineIdGuarder, namespace);
        }
    }
    
    @Override
    public void onLeaseRenewFailed(String namespace, InstanceId instanceId, Throwable failure) {
        if (!ownsInstance(instanceId)) {
            return;
        }
        for (Meters meters : registries) {
            meters.counter("machine.lease.renew.failures", TAG_NAMESPACE, namespace, "Failed machine id lease renewals").increment();
        }
    }
    
    @Override
    public void onLeaseLost(String namespace, InstanceId instanceId) {
        if (!ownsInstance(instanceId)) {
            return;
        }
        for (Meters meters : registries) {
            meters.counter("machine.lease.lost", TAG_NAMESPACE, namespace, "Machine id leases lost to another instance or expired").increment();
        }
    }
    
    @Override
    public void onCallTimeout(String operation, Duration timeout) {
        for (Meters meters : registries) {
            meters.counter("call.timeouts", "operation", operation, "Backend calls that timed out").increment();
        }
    }
    
    @Override
    public void onCallRejected(String operation) {
        for (Meters meters : registries) {
            meters.counter("call.rejections", "operation", operation, "Backend calls rejected: too many in flight").increment();
        }
    }
    
    @Override
    public String toString() {
        return "CosIdMeterBinder{isolated=" + isolated + ", registries=" + registries.size() + '}';
    }
    
    /**
     * The meter id of an event: its name and up to two tags.
     */
    private record Key(String name, String tag1, String value1, String tag2, String value2) {
    }
    
    /**
     * The meters of one registry, looked up once per tag set.
     */
    private static final class Meters {
        private final MeterRegistry registry;
        private final Timer sequenceExhaustedWait;
        private final Map<Key, Counter> counters = new ConcurrentHashMap<>();
        private final Map<Key, Timer> timers = new ConcurrentHashMap<>();
        private final Set<String> leaseNamespaces = ConcurrentHashMap.newKeySet();
        private final List<Meter> registered = new CopyOnWriteArrayList<>();
        
        Meters(MeterRegistry registry) {
            this.registry = registry;
            this.sequenceExhaustedWait = track(Timer.builder(PREFIX + "sequence.exhausted.wait")
                .description("Waits for the next tick after a generator used up its sequence")
                .register(registry));
        }
        
        private <M extends Meter> M track(M meter) {
            registered.add(meter);
            return meter;
        }
        
        Counter counter(String name, String tag, String value, String description) {
            return counter(name, tag, value, "", "", description);
        }
        
        Counter counter(String name, String tag1, String value1, String tag2, String value2, String description) {
            return counters.computeIfAbsent(new Key(name, tag1, value1, tag2, value2), key -> track(Counter.builder(PREFIX + key.name())
                .tags(tags(key))
                .description(description)
                .register(registry)));
        }
        
        Timer timer(String name, String tag1, String value1, String tag2, String value2, String description) {
            return timers.computeIfAbsent(new Key(name, tag1, value1, tag2, value2), key -> track(Timer.builder(PREFIX + key.name())
                .tags(tags(key))
                .description(description)
                .register(registry)));
        }
        
        private static String[] tags(Key key) {
            return key.tag2().isEmpty() ? new String[] {key.tag1(), key.value1()} : new String[] {key.tag1(), key.value1(), key.tag2(), key.value2()};
        }
        
        <T> void gauge(String name, T target, ToDoubleFunction<T> value, String generator, String segment, String description) {
            track(Gauge.builder(PREFIX + name, target, value)
                .tag(TAG_NAME, generator)
                .tag(TAG_SEGMENT, segment)
                .description(description)
                .register(registry));
        }
        
        <T> void generated(String generator, T counted, ToLongFunction<T> count) {
            track(FunctionCounter.builder(PREFIX + "generator.generated", counted, target -> count.applyAsLong(target))
                .tag(TAG_NAME, generator)
                .description("IDs the generator generated")
                .register(registry));
        }
        
        void leaseGauges(MachineIdGuarder guarder, String namespace) {
            if (!leaseNamespaces.add(namespace)) {
                return;
            }
            track(TimeGauge.builder(PREFIX + "machine.lease.remaining", guarder, TimeUnit.MILLISECONDS, target -> remainingMillis(target, namespace))
                .tag(TAG_NAMESPACE, namespace)
                .description("Time left before the machine id lease expires; the Snowflake generators stop at zero, +Inf if it never expires")
                .register(registry));
            track(TimeGauge.builder(PREFIX + "machine.clock.skew", guarder, TimeUnit.MILLISECONDS, target -> clockSkewMillis(target, namespace))
                .tag(TAG_NAMESPACE, namespace)
                .description("How far this host's clock was ahead of the machine id store's at the last renewal")
                .register(registry));
        }
        
        void removeAll() {
            for (Meter meter : registered) {
                registry.remove(meter);
            }
            registered.clear();
            counters.clear();
            timers.clear();
            leaseNamespaces.clear();
        }
    }
}
