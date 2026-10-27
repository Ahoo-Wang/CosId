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
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private LeaseKeeper keeper;
    private StaticSlot staticSlot;
    private ExecutorService ownExecutor;
    private boolean closed;

    private RuntimeParts(String namespace) {
        this.namespace = namespace;
    }

    private record OpenStore(String name, Store store) {
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
                StoreConfig config = new StoreConfig(name, store.type(), store.timeout(), store.options(),
                    Optional.ofNullable(options.clients().get(name)));
                Store opened = providers.find(store.type()).orElseThrow().open(config);
                if (opened == null) {
                    throw new CosIdException("StoreProvider of type [" + store.type() + "] opened no store [" + name + "]");
                }
                parts.stores.add(new OpenStore(name, opened));
            }
            SlotHandle slot = plan.machine().map(machine -> parts.startSlot(machine, plan, options, executor, listener)).orElse(null);
            for (RuntimePlan.Generator generator : plan.generators()) {
                parts.generators.put(generator.name(), parts.create(generator, slot, options, executor, listener));
            }
            log.info("CosIdRuntime [{}] started: stores {}, slot {}, generators {}", spec.namespace(), plan.stores(),
                slot == null ? "none" : slot.current().slot(), parts.generators.keySet());
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

    private SlotHandle startSlot(MachineSpec machine, RuntimePlan plan, RuntimeOptions options, Executor executor,
                                 GeneratorListener listener) {
        switch (machine) {
            case MachineSpec.Leased leased -> {
                Owner owner = new Owner(plan.ownerKey().orElseThrow(), UUID.randomUUID());
                keeper = new LeaseKeeper(store(leased.store()).slots(), namespace, owner, leased.bits(), leased.ttl(),
                    leased.tolerance(), leased.preferredSlot(), leased.ttl(), executor, options.time(), listener);
                keeper.start();
                return keeper;
            }
            case MachineSpec.Static fixed -> {
                staticSlot = new StaticSlot(fixed.slot(), fixed.bits(), fixed.stateFile(), fixed.ahead(), fixed.notBefore(),
                    executor, options.time());
                staticSlot.start();
                return staticSlot;
            }
        }
    }

    private Store store(String name) {
        for (OpenStore open : stores) {
            if (open.name().equals(name)) {
                return open.store();
            }
        }
        throw new IllegalStateException("Store [" + name + "] is not open");
    }

    private GeneratorEntry create(RuntimePlan.Generator generator, SlotHandle slot, RuntimeOptions options, Executor executor,
                                  GeneratorListener listener) {
        String name = generator.name();
        String key = namespace + '/' + name;
        return switch (generator.spec()) {
            case GeneratorSpec.Snowflake snowflake -> GeneratorEntry.snowflake(
                new TimeBasedGenerator(name, snowflake.layout(), slot, options.time(), snowflake.regression(), listener),
                (IdCodec) generator.codec(), slot);
            case GeneratorSpec.CosId80 cosId80 -> GeneratorEntry.cosId80(
                new TimeBasedGenerator(name, Layout.COSID_80, slot, options.time(), cosId80.regression(), listener),
                (PartsCodec) generator.codec(), slot);
            case GeneratorSpec.Segment segment -> GeneratorEntry.segment(
                new SegmentGenerator(name, key, store(segment.store()).ranges(), segment.batch(), executor, options.time(), listener),
                (IdCodec) generator.codec(), segment.store());
            case GeneratorSpec.PeriodicSegment periodic -> GeneratorEntry.periodic(
                new PeriodicSegmentGenerator(name, key, periodic.period(), periodic.zone(), store(periodic.store()).ranges(),
                    periodic.batch(), periodic.periodic(), executor, options.time(), listener),
                (GroupedCodec) generator.codec(), periodic.store(), Map.of(
                    "key", key,
                    "zone", periodic.zone().getId(),
                    "step", periodic.batch().step(),
                    "prewarm", periodic.periodic().prewarm().toString(),
                    "sealAfter", periodic.periodic().sealAfter().map(Object::toString).orElse("")));
            case GeneratorSpec.Disabled disabled -> GeneratorEntry.disabled(name, strategy(disabled.generator()), storeOf(disabled.generator()));
        };
    }

    private static String strategy(GeneratorSpec spec) {
        return switch (spec) {
            case GeneratorSpec.Snowflake snowflake -> GeneratorEntry.SNOWFLAKE;
            case GeneratorSpec.CosId80 cosId80 -> GeneratorEntry.COSID80;
            case GeneratorSpec.Segment segment -> GeneratorEntry.SEGMENT;
            case GeneratorSpec.PeriodicSegment periodic -> GeneratorEntry.PERIODIC_SEGMENT;
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
     * The slot lease of a leased slot.
     *
     * @return the lease, empty with a static slot, without a slot, or once closed
     */
    public Optional<SlotLease> lease() {
        return keeper == null ? Optional.empty() : keeper.lease();
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
        long lastIssued = 0;
        for (GeneratorEntry entry : generators.values()) {
            Optional<TimeBasedGenerator> timeBased = entry.timeBased();
            if (timeBased.isPresent()) {
                run(failures, "close generator [" + entry.name() + "]", entry::close);
                lastIssued = Math.max(lastIssued, timeBased.get().lastIssuedMillis());
            }
        }
        for (GeneratorEntry entry : generators.values()) {
            if (entry.timeBased().isEmpty()) {
                run(failures, "close generator [" + entry.name() + "]", entry::close);
            }
        }
        // 2. Release the slot with the exact largest timestamp issued on it.
        long released = lastIssued;
        if (keeper != null) {
            run(failures, "release the slot", () -> keeper.close(released));
        }
        if (staticSlot != null) {
            run(failures, "close the static slot", () -> staticSlot.close(released));
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
