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

import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.SegmentGenerator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

/**
 * The Micrometer metrics (07 §2, 11 §5): a {@link GeneratorListener} for events and gauges on the runtime's state,
 * replacing the 4.x {@code CosIdMeterBinder}. Every meter is named {@code cosid.*}:
 *
 * <table>
 *     <caption>Meters</caption>
 *     <tr><th>Meter</th><th>Tags</th><th>4.x</th></tr>
 *     <tr><td>{@code cosid.segment.reserve} (timer)</td><td>name</td><td>{@code segment.fetch}</td></tr>
 *     <tr><td>{@code cosid.segment.refill.failures}</td><td>name, error</td><td>{@code segment.prefetch.failures}</td></tr>
 *     <tr><td>{@code cosid.segment.batch.resizes}</td><td>name, direction</td><td>{@code segment.prefetch.expansions}</td></tr>
 *     <tr><td>{@code cosid.segment.batch} (gauge)</td><td>name</td><td>{@code segment.prefetch.distance}</td></tr>
 *     <tr><td>{@code cosid.segment.held} (gauge)</td><td>name</td><td>{@code segment.remaining}</td></tr>
 *     <tr><td>{@code cosid.sequence.exhausted}</td><td>name</td><td>{@code sequence.exhausted.wait}</td></tr>
 *     <tr><td>{@code cosid.clock.backwards} (summary, ms)</td><td>name, action</td><td>{@code clock.backwards}, {@code clock.sync.wait}</td></tr>
 *     <tr><td>{@code cosid.machine.window.exceeded}</td><td>name</td><td>—</td></tr>
 *     <tr><td>{@code cosid.machine.lease.acquired}</td><td>namespace, machine, reason</td><td>{@code machine.lease.acquired}</td></tr>
 *     <tr><td>{@code cosid.machine.lease.renewed}, {@code .released}</td><td>namespace, machine</td><td>—</td></tr>
 *     <tr><td>{@code cosid.machine.lease.renew.failures}</td><td>namespace, machine</td><td>{@code machine.lease.renew.failures}</td></tr>
 *     <tr><td>{@code cosid.machine.lease.lost}</td><td>namespace, machine</td><td>{@code machine.lease.lost}</td></tr>
 *     <tr><td>{@code cosid.machine.lease.remaining} (time gauge)</td><td>namespace, machine</td><td>{@code machine.lease.remaining}</td></tr>
 *     <tr><td>{@code cosid.machine.window.remaining} (time gauge)</td><td>namespace, machine</td><td>—</td></tr>
 *     <tr><td>{@code cosid.machine.clock.skew} (time gauge)</td><td>namespace, machine</td><td>{@code machine.clock.skew}</td></tr>
 *     <tr><td>{@code cosid.machine.owner.duplicates}</td><td>namespace, machine</td><td>—</td></tr>
 *     <tr><td>{@code cosid.store.timeouts}</td><td>store, operation</td><td>{@code call.timeouts}</td></tr>
 *     <tr><td>{@code cosid.generator.generated} (function counter, {@code cosid.metrics.generated.enabled})</td><td>name</td><td>{@code generator.generated}</td></tr>
 * </table>
 *
 * <p>{@code cosid.generator.generated} counts every view the runtime hands out, the {@code IdFiller} among them. The
 * gauges are registered once every singleton exists, the runtime among them; the {@code cosid.machine.*} gauges once per
 * started machine. The {@code machine} tag is {@code default} or the name of a named machine (D37): the namespace of a
 * machine is unique in a runtime, so it tells the machine.
 */
public class CosIdMeterListener implements GeneratorListener, SmartInitializingSingleton {
    public static final String PREFIX = "cosid.";

    private final MeterRegistry registry;
    private final ObjectProvider<CosIdRuntime> runtime;
    private final ObjectProvider<RuntimeSpec> spec;
    private final Map<String, AtomicLong> clockSkews = new ConcurrentHashMap<>();
    private volatile Map<String, String> machines;

    /**
     * The listener of a runtime with the default machine only.
     *
     * @param registry the registry
     * @param runtime  the runtime, for the gauges and {@code cosid.generator.generated}
     */
    public CosIdMeterListener(MeterRegistry registry, ObjectProvider<CosIdRuntime> runtime) {
        this(registry, runtime, null);
    }

    /**
     * The listener.
     *
     * @param registry the registry
     * @param runtime  the runtime, for the gauges and {@code cosid.generator.generated}
     * @param spec     the spec of the runtime, for the {@code machine} tag of a namespace; events come while the runtime
     *                 starts, before it exists
     */
    public CosIdMeterListener(MeterRegistry registry, ObjectProvider<CosIdRuntime> runtime, ObjectProvider<RuntimeSpec> spec) {
        this.registry = registry;
        this.runtime = runtime;
        this.spec = spec;
    }

    /**
     * The machine names by the namespaces of their slots (api-freeze.md §2.4).
     *
     * @param spec the spec
     * @return the names by namespace
     */
    static Map<String, String> machinesByNamespace(RuntimeSpec spec) {
        Map<String, String> names = new HashMap<>();
        spec.machines().forEach((name, machine) -> names.put(machine.namespace().orElse(spec.namespace()), name));
        spec.machine().ifPresent(machine -> names.put(machine.namespace().orElse(spec.namespace()), RuntimeSpec.DEFAULT_MACHINE));
        return names;
    }

    private String machine(String namespace) {
        Map<String, String> names = machines;
        if (names == null) {
            RuntimeSpec current = spec == null ? null : spec.getIfAvailable();
            names = current == null ? Map.of() : machinesByNamespace(current);
            machines = names;
        }
        return names.getOrDefault(namespace, RuntimeSpec.DEFAULT_MACHINE);
    }

    private AtomicLong clockSkew(String namespace) {
        return clockSkews.computeIfAbsent(namespace, k -> new AtomicLong());
    }

    @Override
    public void afterSingletonsInstantiated() {
        CosIdRuntime current = runtime.getIfAvailable();
        if (current == null) {
            return;
        }
        for (Map.Entry<String, GeneratorInfo> entry : current.describe().entrySet()) {
            String name = entry.getKey();
            GeneratorInfo info = entry.getValue();
            if (Boolean.TRUE.equals(info.details().get("disabled"))) {
                continue;
            }
            if (CosIdSpecs.SEGMENT.equals(info.strategy())) {
                SegmentGenerator segment = current.get(name, SegmentGenerator.class);
                gauge("segment.held", segment, generator -> Math.max(0, generator.held()), name, "Ids left in the ranges the generator holds");
                gauge("segment.batch", segment, SegmentGenerator::batch, name, "The size of the generator's next reservation");
            }
            if (current.generated(name).isPresent()) {
                FunctionCounter.builder(PREFIX + "generator.generated", current, target -> target.generated(name).orElse(0))
                    .tag("name", name).description("Ids the generator issued").register(registry);
            }
        }
        // One group per started leased machine.
        for (String machine : current.machines()) {
            Optional<SlotLease> held = current.slot(machine);
            if (held.isEmpty()) {
                continue;
            }
            String machineNamespace = held.get().namespace();
            TimeGauge.builder(PREFIX + "machine.lease.remaining", current, TimeUnit.MILLISECONDS,
                    target -> target.slot(machine).map(lease -> (double) lease.expiresAt() - System.currentTimeMillis()).orElse(0.0))
                .tags("namespace", machineNamespace, "machine", machine).description("Until the slot lease expires").register(registry);
            TimeGauge.builder(PREFIX + "machine.window.remaining", current, TimeUnit.MILLISECONDS,
                    target -> target.slot(machine).map(lease -> (double) lease.ceiling() - System.currentTimeMillis()).orElse(0.0))
                .tags("namespace", machineNamespace, "machine", machine).description("Until the slot window ceiling").register(registry);
            TimeGauge.builder(PREFIX + "machine.clock.skew", clockSkew(machineNamespace), TimeUnit.MILLISECONDS, AtomicLong::get)
                .tags("namespace", machineNamespace, "machine", machine).description("The last measured skew between the local and the store clock")
                .register(registry);
        }
    }

    private <T> void gauge(String name, T target, ToDoubleFunction<T> value, String generator, String description) {
        Gauge.builder(PREFIX + name, target, value).tag("name", generator).description(description).register(registry);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(PREFIX + name).tags(tags).register(registry);
    }

    @Override
    public void onRangeReserved(String generator, String key, Range range, Duration latency) {
        // No key tag (R1-spring-06): a periodic segment has a key per period, an unbounded tag.
        Timer.builder(PREFIX + "segment.reserve").tags("name", generator).description("Range reservations")
            .register(registry).record(latency);
    }

    @Override
    public void onRefillFailed(String generator, String key, Throwable error) {
        counter("segment.refill.failures", "name", generator, "error", error.getClass().getSimpleName()).increment();
    }

    @Override
    public void onBatchResized(String generator, long oldSize, long newSize) {
        counter("segment.batch.resizes", "name", generator, "direction", newSize > oldSize ? "up" : "down").increment();
    }

    @Override
    public void onSequenceExhausted(String generator) {
        counter("sequence.exhausted", "name", generator).increment();
    }

    @Override
    public void onClockRegression(String generator, long backwardsMillis, String action) {
        DistributionSummary.builder(PREFIX + "clock.backwards").baseUnit("milliseconds").tags("name", generator, "action", action)
            .description("Clock regressions and how far back").register(registry).record(backwardsMillis);
    }

    @Override
    public void onWindowExceeded(String generator) {
        counter("machine.window.exceeded", "name", generator).increment();
    }

    @Override
    public void onSlotAcquired(SlotLease lease, String reason) {
        counter("machine.lease.acquired", "namespace", lease.namespace(), "machine", machine(lease.namespace()), "reason", reason).increment();
    }

    @Override
    public void onSlotRenewed(SlotLease lease) {
        counter("machine.lease.renewed", "namespace", lease.namespace(), "machine", machine(lease.namespace())).increment();
    }

    @Override
    public void onSlotRenewFailed(SlotLease lease, Throwable error) {
        counter("machine.lease.renew.failures", "namespace", lease.namespace(), "machine", machine(lease.namespace())).increment();
    }

    @Override
    public void onSlotLost(SlotLease lease) {
        counter("machine.lease.lost", "namespace", lease.namespace(), "machine", machine(lease.namespace())).increment();
    }

    @Override
    public void onSlotReleased(SlotLease lease) {
        counter("machine.lease.released", "namespace", lease.namespace(), "machine", machine(lease.namespace())).increment();
    }

    @Override
    public void onClockSkew(String namespace, long skewMillis) {
        clockSkew(namespace).set(skewMillis);
    }

    @Override
    public void onDuplicateOwnerKey(String namespace, String ownerKey) {
        counter("machine.owner.duplicates", "namespace", namespace, "machine", machine(namespace)).increment();
    }

    @Override
    public void onStoreTimeout(String store, String operation) {
        counter("store.timeouts", "store", store, "operation", operation).increment();
    }
}
