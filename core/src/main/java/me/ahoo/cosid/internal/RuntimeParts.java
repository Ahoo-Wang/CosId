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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.lease.LeaseKeeper;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.StaticSlot;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.time.TimeBasedGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * What a runtime is made of, assembled in order (stores, the machine slot, generators) and closed in reverse
 * (07-runtime-and-spring.md §1, 05-machine-lease.md §4.3). A failed start closes what it created.
 */
public final class RuntimeParts {
    private static final Logger log = LoggerFactory.getLogger(RuntimeParts.class);
    /** How long close waits for the tasks of the runtime's own executor. */
    private static final long EXECUTOR_TERMINATION_SECONDS = 10;

    private final String namespace;
    private final List<OpenStore> stores = new ArrayList<>();
    private final Map<String, GeneratorEntry> generators = new LinkedHashMap<>();
    /** The started machines, in start order: the default one first. */
    private final List<StartedMachine> machines = new ArrayList<>();
    private ExecutorService ownExecutor;
    private boolean closed;

    private RuntimeParts(String namespace) {
        this.namespace = namespace;
    }

    private record OpenStore(String name, Store store) {
    }

    /** A started machine: a lease keeper or a static slot, and the node offset of its generators. */
    private record StartedMachine(String name, String namespace, SlotHandle slot, LeaseKeeper keeper, StaticSlot staticSlot, int nodeOffset) {
    }

    /**
     * The runtime's own executor: a virtual thread per task, named after the namespace.
     *
     * @param namespace the namespace
     * @return the executor
     */
    public static ExecutorService ownExecutor(String namespace) {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("cosid-" + namespace + "-", 0).factory());
    }

    /**
     * Checks the spec, then opens the stores, acquires the slot and creates the generators; on failure closes what was
     * created and rethrows.
     *
     * @param spec         the spec
     * @param options      the options
     * @param ownExecutors creates the runtime's own executor when the options have none
     * @return the parts
     * @throws CosIdException if the spec is invalid or a step fails
     */
    public static RuntimeParts assemble(RuntimeSpec spec, RuntimeOptions options, Function<String, ExecutorService> ownExecutors) {
        StoreProviders providers = new StoreProviders(options.storeProviders());
        RuntimePlan plan = RuntimePlan.of(spec, options, providers);
        GeneratorListener listener = SafeListener.of(options.listener());
        RuntimeParts parts = new RuntimeParts(spec.namespace());
        try {
            if (options.executor().isEmpty()) {
                parts.ownExecutor = ownExecutors.apply(spec.namespace());
            }
            Executor executor = options.executor().orElse(parts.ownExecutor);
            for (String name : plan.stores()) {
                StoreSpec store = spec.stores().get(name);
                StoreConfig.Builder config = StoreConfig.builder(name, store.type()).timeout(store.timeout()).options(plan.storeOptions(name));
                Optional.ofNullable(options.clients().get(name)).ifPresent(config::client);
                Store opened = providers.find(store.type()).orElseThrow().open(config.build());
                if (opened == null) {
                    throw new CosIdException("StoreProvider of type [" + store.type() + "] opened no store [" + name + "]");
                }
                parts.stores.add(new OpenStore(name, opened));
            }
            // One after the other (api-freeze.md §2.3): each blocks until it holds its slot or its acquire timeout.
            for (RuntimePlan.Machine machine : plan.machines()) {
                parts.machines.add(parts.startMachine(machine, options, executor, listener));
            }
            for (RuntimePlan.Generator generator : plan.generators()) {
                parts.generators.put(generator.name(), parts.create(generator, options, executor, listener));
            }
            Map<String, Object> slots = new LinkedHashMap<>();
            parts.machines.forEach(machine -> slots.put(machine.name(), machine.namespace() + "#" + machine.slot().current().slot()));
            log.info("CosIdRuntime [{}] started: stores {}, slots {}, generators {}", spec.namespace(), plan.stores(), slots.isEmpty() ? "none" : slots,
                parts.generators.keySet());
            return parts;
        } catch (RuntimeException | Error e) {
            for (Exception failure : parts.closeAll()) {
                e.addSuppressed(failure);
            }
            if (e instanceof CosIdException || e instanceof Error) {
                throw e;
            }
            throw new CosIdException("CosIdRuntime [" + spec.namespace() + "] failed to start: " + e.getMessage(), e);
        }
    }

    private StartedMachine startMachine(RuntimePlan.Machine machine, RuntimeOptions options, Executor executor, GeneratorListener listener) {
        switch (machine.spec()) {
            case MachineSpec.Leased leased -> {
                Owner owner = new Owner(machine.ownerKey().orElseThrow(), UUID.randomUUID());
                LeaseKeeper keeper = new LeaseKeeper(ObservedStores.slots(leased.store(), store(leased.store()).slots(), listener), machine.namespace(), owner,
                    leased.bits(), leased.ttl(), leased.tolerance(), leased.slotOffset(), leased.preferredSlot(), leased.acquireTimeout(), executor,
                    options.time(), listener);
                keeper.start();
                return new StartedMachine(machine.name(), machine.namespace(), keeper, keeper, null, leased.slotOffset());
            }
            case MachineSpec.Static fixed -> {
                StaticSlot staticSlot = new StaticSlot(fixed.slot(), fixed.bits(), fixed.stateFile(), fixed.ahead(), fixed.notBefore(),
                    executor, options.time());
                staticSlot.start();
                return new StartedMachine(machine.name(), machine.namespace(), staticSlot, null, staticSlot, 0);
            }
        }
    }

    private StartedMachine machine(String name) {
        for (StartedMachine machine : machines) {
            if (machine.name().equals(name)) {
                return machine;
            }
        }
        throw new IllegalStateException("Machine [" + name + "] is not started");
    }

    private Store store(String name) {
        for (OpenStore open : stores) {
            if (open.name().equals(name)) {
                return open.store();
            }
        }
        throw new IllegalStateException("Store [" + name + "] is not open");
    }

    /** The range store {@code name} as generators use it: its timeouts reach the listener. */
    private RangeStore ranges(String name, GeneratorListener listener) {
        return ObservedStores.ranges(name, store(name).ranges(), listener);
    }

    private GeneratorEntry create(RuntimePlan.Generator generator, RuntimeOptions options, Executor executor, GeneratorListener listener) {
        String name = generator.name();
        String key = namespace + '/' + name;
        LongAdder counter = options.countGenerated() ? new LongAdder() : null;
        return switch (generator.spec()) {
            case GeneratorSpec.Snowflake snowflake -> {
                StartedMachine machine = machine(generator.machine());
                yield GeneratorEntry.snowflake(
                    new TimeBasedGenerator(name, snowflake.layout(), machine.slot(), machine.nodeOffset(), options.time(), snowflake.regression(), listener),
                    (IdCodec) generator.codec(), machine.slot(), machine.name(), machine.namespace(), counter);
            }
            case GeneratorSpec.CosId80 cosId80 -> {
                StartedMachine machine = machine(generator.machine());
                yield GeneratorEntry.cosId80(
                    new TimeBasedGenerator(name, cosId80.layout(), machine.slot(), machine.nodeOffset(), options.time(), cosId80.regression(), listener),
                    (PartsCodec) generator.codec(), machine.slot(), machine.name(), machine.namespace(), counter);
            }
            case GeneratorSpec.Segment segment -> GeneratorEntry.segment(
                new SegmentGenerator(name, key, segment.store(), ranges(segment.store(), listener), segment.batch(), executor, options.time(),
                    listener),
                (IdCodec) generator.codec(), segment.store(), counter);
            case GeneratorSpec.PeriodicSegment periodic -> GeneratorEntry.periodic(
                new PeriodicSegmentGenerator(name, key, periodic.store(), periodic.period(), periodic.zone(), ranges(periodic.store(), listener),
                    periodic.batch(), periodic.periodic(), executor, options.time(), listener),
                (GroupedCodec) generator.codec(), periodic.store(), Map.of(
                    "key", key,
                    "zone", periodic.zone().getId(),
                    "step", periodic.batch().step(),
                    "maxBatch", periodic.batch().maxBatch(),
                    "lookahead", periodic.batch().lookahead().toString(),
                    "reserveTimeout", periodic.batch().reserveTimeout().toString(),
                    "prewarm", periodic.periodic().prewarm().toString(),
                    "sealAfter", periodic.periodic().sealAfter().map(Object::toString).orElse("")), counter);
            case GeneratorSpec.Custom custom -> GeneratorEntry.custom(name, generator.ids(), (IdCodec) generator.codec(), counter);
            case GeneratorSpec.Instance instance -> GeneratorEntry.custom(name, generator.ids(), (IdCodec) generator.codec(), counter);
            case GeneratorSpec.UuidV7 uuidV7 -> GeneratorEntry.uuidV7(name, options.time(), counter);
            case GeneratorSpec.Disabled disabled -> GeneratorEntry.disabled(name, strategy(disabled.generator()), storeOf(disabled.generator()));
        };
    }

    private static String strategy(GeneratorSpec spec) {
        return switch (spec) {
            case GeneratorSpec.Snowflake snowflake -> GeneratorEntry.SNOWFLAKE;
            case GeneratorSpec.CosId80 cosId80 -> GeneratorEntry.COSID80;
            case GeneratorSpec.Segment segment -> GeneratorEntry.SEGMENT;
            case GeneratorSpec.PeriodicSegment periodic -> GeneratorEntry.PERIODIC_SEGMENT;
            case GeneratorSpec.Custom custom -> GeneratorEntry.CUSTOM;
            case GeneratorSpec.Instance instance -> GeneratorEntry.CUSTOM;
            case GeneratorSpec.UuidV7 uuidV7 -> GeneratorEntry.UUIDV7;
            case GeneratorSpec.Disabled disabled -> strategy(disabled.generator());
        };
    }

    private static String storeOf(GeneratorSpec spec) {
        return switch (spec) {
            case GeneratorSpec.Segment segment -> segment.store();
            case GeneratorSpec.PeriodicSegment periodic -> periodic.store();
            default -> "";
        };
    }

    public String namespace() {
        return namespace;
    }

    /**
     * The generators by name, in name order.
     *
     * @return the generators
     */
    public Map<String, GeneratorEntry> generators() {
        return Collections.unmodifiableMap(generators);
    }

    /**
     * The slot lease of the default machine, when it is leased.
     *
     * @return the lease, empty with a static slot, without a slot, or once closed
     */
    public Optional<SlotLease> lease() {
        return lease(RuntimeSpec.DEFAULT_MACHINE);
    }

    /**
     * The slot lease of a started machine, when it is leased.
     *
     * @param machine the machine name, {@value RuntimeSpec#DEFAULT_MACHINE} for the default one
     * @return the lease, empty with a static slot, for a machine that did not start, or once closed
     */
    public Optional<SlotLease> lease(String machine) {
        for (StartedMachine started : machines) {
            if (started.name().equals(machine)) {
                return started.keeper() == null ? Optional.empty() : started.keeper().lease();
            }
        }
        return Optional.empty();
    }

    /**
     * The started machines by name, in start order, with their namespaces.
     *
     * @return the namespaces by machine name
     */
    public Map<String, String> machines() {
        Map<String, String> names = new LinkedHashMap<>();
        machines.forEach(machine -> names.put(machine.name(), machine.namespace()));
        return Collections.unmodifiableMap(names);
    }

    /**
     * Closes in reverse dependency order (05-machine-lease.md §4.3): every time-based generator, then the other
     * generators, then the slot released with the exact largest timestamp the generators issued, then the stores in
     * reverse order, then the own executor. Every step runs even if an earlier one fails. Closing again does nothing.
     *
     * @throws CosIdException if a step failed, with each failure suppressed
     */
    public void close() {
        List<Exception> failures = closeAll();
        if (!failures.isEmpty()) {
            CosIdException failed = new CosIdException("CosIdRuntime [" + namespace + "] closed with " + failures.size() + " failure(s)",
                failures.getFirst());
            failures.stream().skip(1).forEach(failed::addSuppressed);
            throw failed;
        }
    }

    private synchronized List<Exception> closeAll() {
        List<Exception> failures = new ArrayList<>();
        if (closed) {
            return failures;
        }
        closed = true;
        // 1. Time-based generators reach their terminal state: their last issued time is exact from here on.
        Map<String, Long> lastIssued = new HashMap<>();
        for (GeneratorEntry entry : generators.values()) {
            Optional<TimeBasedGenerator> timeBased = entry.timeBased();
            if (timeBased.isPresent()) {
                run(failures, "close generator [" + entry.name() + "]", entry::close);
                lastIssued.merge(entry.machine().orElseThrow(), timeBased.get().lastIssuedMillis(), Math::max);
            }
        }
        for (GeneratorEntry entry : generators.values()) {
            if (entry.timeBased().isEmpty()) {
                run(failures, "close generator [" + entry.name() + "]", entry::close);
            }
        }
        // 2. Release each machine in reverse start order, with the exact largest timestamp its own generators issued
        //    (api-freeze.md §2.3): another machine's timestamps would raise its next floor for nothing.
        for (int i = machines.size() - 1; i >= 0; i--) {
            StartedMachine machine = machines.get(i);
            long released = lastIssued.getOrDefault(machine.name(), 0L);
            if (machine.keeper() != null) {
                run(failures, "release the slot of machine [" + machine.name() + "]", () -> machine.keeper().close(released));
            } else {
                run(failures, "close the static slot of machine [" + machine.name() + "]", () -> machine.staticSlot().close(released));
            }
        }
        // 3. Stores, in reverse order of opening.
        for (int i = stores.size() - 1; i >= 0; i--) {
            OpenStore open = stores.get(i);
            run(failures, "close store [" + open.name() + "]", open.store()::close);
        }
        // 4. The runtime's own executor.
        if (ownExecutor != null) {
            run(failures, "stop the executor", this::stopOwnExecutor);
        }
        return failures;
    }

    private void stopOwnExecutor() throws InterruptedException {
        ownExecutor.shutdownNow();
        if (!ownExecutor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
            throw new CosIdException("The executor of CosIdRuntime [" + namespace + "] did not terminate in "
                + EXECUTOR_TERMINATION_SECONDS + "s");
        }
    }

    private void run(List<Exception> failures, String step, Step action) {
        try {
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(e);
        } catch (Exception e) {
            log.warn("CosIdRuntime [{}] failed to {}", namespace, step, e);
            failures.add(e);
        }
    }

    @FunctionalInterface
    private interface Step {
        void run() throws Exception;
    }
}
