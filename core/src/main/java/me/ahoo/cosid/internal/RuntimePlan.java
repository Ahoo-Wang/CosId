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
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.HostResolver;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.time.Layout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A {@link RuntimeSpec} checked as a whole before anything starts (07-runtime-and-spring.md §1): every problem is
 * reported at once, and the codecs are built. Only the stores the enabled generators and the started machines use are
 * opened, and only the machines an enabled time-based generator chooses are started (api-freeze.md §2.2).
 */
public final class RuntimePlan {
    private static final Logger log = LoggerFactory.getLogger(RuntimePlan.class);
    /** The slot bits a lease keeper accepts. */
    static final int MAX_MACHINE_BITS = Integer.SIZE - 2;

    private final List<Generator> generators;
    private final List<String> stores;
    private final List<Machine> machines;

    private RuntimePlan(List<Generator> generators, List<String> stores, List<Machine> machines) {
        this.generators = List.copyOf(generators);
        this.stores = List.copyOf(stores);
        this.machines = List.copyOf(machines);
    }

    /**
     * A generator to create.
     *
     * @param name    the name
     * @param spec    the spec; a {@link GeneratorSpec.Disabled} one is not created
     * @param codec   the {@code IdCodec}, {@code PartsCodec} or {@code GroupedCodec} by strategy; null when disabled
     * @param ids     the user generator of a {@link GeneratorSpec.Custom} (resolved) or {@link GeneratorSpec.Instance};
     *                null otherwise
     * @param machine the machine a time-based generator issues on; null for other strategies
     */
    public record Generator(String name, GeneratorSpec spec, Object codec, IdGenerator ids, String machine) {
    }

    /**
     * A machine to start.
     *
     * @param name      {@value RuntimeSpec#DEFAULT_MACHINE} or the name of a named machine
     * @param spec      the machine
     * @param namespace the namespace of its slot, unique among the machines of the runtime
     * @param ownerKey  the owner key of a leased slot (07 §1.1); empty for a static one
     */
    public record Machine(String name, MachineSpec spec, String namespace, Optional<String> ownerKey) {
    }

    /**
     * The generators in name order.
     *
     * @return the generators
     */
    public List<Generator> generators() {
        return generators;
    }

    /**
     * The stores to open, in name order.
     *
     * @return the store names
     */
    public List<String> stores() {
        return stores;
    }

    /**
     * The machines to start, in start order: the default machine, then the named ones in name order.
     *
     * @return the machines
     */
    public List<Machine> machines() {
        return machines;
    }

    /**
     * Checks {@code spec} and builds its codecs.
     *
     * @param spec      the spec
     * @param options   the options
     * @param providers the store providers
     * @return the plan
     * @throws CosIdException listing every problem of the spec
     */
    public static RuntimePlan of(RuntimeSpec spec, RuntimeOptions options, StoreProviders providers) {
        List<String> problems = new ArrayList<>();
        Codecs codecs = new Codecs(options.time(), options.codecs());
        TreeSet<String> usedStores = new TreeSet<>();
        List<Generator> generators = new ArrayList<>();
        // The generators of each chosen machine, by machine name.
        Map<String, List<String>> chosen = new TreeMap<>();
        for (Map.Entry<String, GeneratorSpec> entry : new TreeMap<>(spec.generators()).entrySet()) {
            String name = entry.getKey();
            GeneratorSpec generator = entry.getValue();
            if (name.isBlank() || name.indexOf('/') >= 0) {
                problems.add("generator [" + name + "]: the name must not be blank nor contain '/'");
                continue;
            }
            if (generator instanceof GeneratorSpec.Disabled) {
                generators.add(new Generator(name, generator, null, null, null));
                continue;
            }
            String at = "generator [" + name + "]: ";
            Object codec = null;
            IdGenerator ids = null;
            String machineName = null;
            try {
                switch (generator) {
                    case GeneratorSpec.Snowflake snowflake -> {
                        Layout layout = snowflake.layout();
                        machineName = chooseMachine(spec, name, snowflake.machine(), layout, at, problems, chosen);
                        if (layout.totalBits() > Layout.LONG_BITS) {
                            problems.add(at + "a snowflake layout must fit a long, not " + layout.totalBits() + " bits: use cosid80");
                        }
                        checkEpoch(layout, options, at, problems);
                        codec = codecs.ids(snowflake.codec(), Optional.of(layout));
                    }
                    case GeneratorSpec.CosId80 cosId80 -> {
                        Layout layout = cosId80.layout();
                        machineName = chooseMachine(spec, name, cosId80.machine(), layout, at, problems, chosen);
                        if (layout.totalBits() <= Layout.LONG_BITS) {
                            problems.add(at + "a cosid80 layout must be wider than a long, not " + layout.totalBits() + " bits: it fits a long, use snowflake");
                        }
                        // The 80-bit text decodes alike in every CosId implementation (D4, D36): 4.x used the Unix epoch in ms.
                        if (!layout.epoch().equals(Instant.EPOCH) || layout.unit() != ChronoUnit.MILLIS) {
                            problems.add(at + "a cosid80 layout keeps the Unix epoch and milliseconds, not " + layout.epoch() + " in " + layout.unit());
                        }
                        // The sequencer packs the time and the sequence in a long (TimeSequencer).
                        if (layout.timeBits() + layout.seqBits() > Layout.LONG_BITS) {
                            problems.add(at + "timeBits + seqBits of a cosid80 layout must be at most " + Layout.LONG_BITS + ", not "
                                + (layout.timeBits() + layout.seqBits()));
                        }
                        codec = Codecs.parts(cosId80.codec(), layout);
                    }
                    case GeneratorSpec.Segment segment -> {
                        checkStore(spec, segment.store(), segment.batch(), at, problems, usedStores);
                        codec = codecs.ids(segment.codec(), Optional.empty());
                    }
                    case GeneratorSpec.PeriodicSegment periodic -> {
                        checkStore(spec, periodic.store(), periodic.batch(), at, problems, usedStores);
                        codec = codecs.grouped(periodic.codec(), periodic.period());
                    }
                    case GeneratorSpec.Custom custom -> {
                        codec = codecs.ids(custom.codec(), Optional.empty());
                        ids = options.generators().apply(custom.reference()).orElse(null);
                        if (ids == null) {
                            problems.add(at + "no generator [" + custom.reference() + "] to refer to");
                        }
                    }
                    case GeneratorSpec.Instance instance -> {
                        codec = codecs.ids(instance.codec(), Optional.empty());
                        ids = instance.ids();
                    }
                    case GeneratorSpec.Disabled disabled -> throw new IllegalStateException("handled above");
                }
            } catch (IllegalArgumentException e) {
                problems.add(at + e.getMessage());
            }
            generators.add(new Generator(name, generator, codec, ids, machineName));
        }

        final List<Machine> machines = startOrder(spec, options, chosen, problems, usedStores);

        for (String store : usedStores) {
            StoreSpec storeSpec = spec.stores().get(store);
            if (!isPositive(storeSpec.timeout())) {
                problems.add("store [" + store + "]: timeout must be positive: " + storeSpec.timeout());
            }
            if (providers.find(storeSpec.type()).isEmpty()) {
                problems.add("store [" + store + "]: no StoreProvider of type [" + storeSpec.type() + "]");
            }
        }
        for (String client : new TreeSet<>(options.clients().keySet())) {
            if (!spec.stores().containsKey(client)) {
                problems.add("clients: [" + client + "] is not a store of the spec");
            }
        }
        if (!problems.isEmpty()) {
            throw new CosIdException("Invalid RuntimeSpec of namespace [" + spec.namespace() + "]:\n  - " + String.join("\n  - ", problems));
        }
        return new RuntimePlan(generators, new ArrayList<>(usedStores), machines);
    }

    /**
     * The machine a time-based generator chooses, recorded in {@code chosen}, with its node bits checked against it:
     * {@code slotOffset + 2^bits <= 2^nodeBits} (api-freeze.md §2.2 item 5).
     */
    private static String chooseMachine(RuntimeSpec spec, String generator, Optional<String> choice, Layout layout, String at, List<String> problems,
                                        Map<String, List<String>> chosen) {
        String name = choice.orElse(RuntimeSpec.DEFAULT_MACHINE);
        boolean isDefault = RuntimeSpec.DEFAULT_MACHINE.equals(name);
        MachineSpec machine = isDefault ? spec.machine().orElse(null) : spec.machines().get(name);
        if (machine == null && !isDefault) {
            problems.add(at + "no machine [" + name + "]");
            return name;
        }
        chosen.computeIfAbsent(name, k -> new ArrayList<>()).add(generator);
        if (machine == null || machine.bits() < 1 || machine.bits() > MAX_MACHINE_BITS) {
            return name;
        }
        int offset = machine instanceof MachineSpec.Leased leased ? Math.max(0, leased.slotOffset()) : 0;
        if (offset + (1L << machine.bits()) > 1L << layout.nodeBits()) {
            String bits = isDefault ? "machine.bits " : "machines." + name + ".bits ";
            problems.add(offset == 0
                ? at + "the layout has " + layout.nodeBits() + " node bits, fewer than " + bits + machine.bits()
                : at + "the layout has " + layout.nodeBits() + " node bits, too few for slotOffset " + offset + " + 2^" + machine.bits()
                    + " slots of machine [" + name + "]");
        }
        return name;
    }

    /**
     * The machines to start (api-freeze.md §2.2, §2.3): the chosen ones, the default first, each checked, with a namespace
     * of its own.
     */
    private static List<Machine> startOrder(RuntimeSpec spec, RuntimeOptions options, Map<String, List<String>> chosen, List<String> problems,
                                          TreeSet<String> usedStores) {
        for (String name : new TreeSet<>(spec.machines().keySet())) {
            if (name.isBlank() || name.indexOf('/') >= 0 || RuntimeSpec.DEFAULT_MACHINE.equals(name)) {
                problems.add("machines." + name + ": the name must not be blank, contain '/' nor be [" + RuntimeSpec.DEFAULT_MACHINE + "]");
            }
        }
        List<String> unused = new ArrayList<>(new TreeSet<>(spec.machines().keySet()));
        unused.removeAll(chosen.keySet());
        if (!unused.isEmpty()) {
            log.info("Machines {} of namespace [{}] are chosen by no enabled generator: they do not start", unused, spec.namespace());
        }
        List<Machine> machines = new ArrayList<>();
        Map<String, String> namespaces = new HashMap<>();
        Map<Path, String> stateFiles = new HashMap<>();
        // The default machine first, then the named ones in name order (TreeMap).
        List<String> order = new ArrayList<>();
        if (chosen.containsKey(RuntimeSpec.DEFAULT_MACHINE)) {
            order.add(RuntimeSpec.DEFAULT_MACHINE);
        }
        chosen.keySet().stream().filter(name -> !RuntimeSpec.DEFAULT_MACHINE.equals(name)).forEach(order::add);
        for (String name : order) {
            boolean isDefault = RuntimeSpec.DEFAULT_MACHINE.equals(name);
            MachineSpec machine = isDefault ? spec.machine().orElse(null) : spec.machines().get(name);
            if (machine == null) {
                problems.add("machine: time-based generators " + chosen.get(name) + " need a machine slot, but the spec has no machine");
                continue;
            }
            String at = isDefault ? "machine: " : "machines." + name + ": ";
            checkMachine(spec, machine, at, problems, usedStores);
            String namespace = machine.namespace().orElse(spec.namespace());
            if (namespace.isBlank()) {
                problems.add(at + "namespace must not be blank");
            }
            String other = namespaces.putIfAbsent(namespace, name);
            if (other != null) {
                problems.add(at + "namespace [" + namespace + "] is also the namespace of machine [" + other + "]: each machine needs its own");
            }
            if (machine instanceof MachineSpec.Static fixed && fixed.stateFile().isPresent()) {
                String sharing = stateFiles.putIfAbsent(fixed.stateFile().get().toAbsolutePath().normalize(), name);
                if (sharing != null) {
                    problems.add(at + "stateFile " + fixed.stateFile().get() + " is also the state file of machine [" + sharing + "]");
                }
            }
            Optional<String> ownerKey = machine instanceof MachineSpec.Leased leased
                ? Optional.of(deriveOwnerKey(leased.ownerKey(), options.hostResolver(), options.port()))
                : Optional.empty();
            machines.add(new Machine(name, machine, namespace, ownerKey));
        }
        return machines;
    }

    /** An epoch in the future fails the start (R1-core-12): the sequencer takes a clock before the epoch as a regression. */
    private static void checkEpoch(Layout layout, RuntimeOptions options, String at, List<String> problems) {
        if (layout.epoch().toEpochMilli() > options.time().currentMillis()) {
            problems.add(at + "the epoch " + layout.epoch() + " is in the future");
        }
    }

    private static void checkStore(RuntimeSpec spec, String store, BatchPolicy batch, String at, List<String> problems,
                                   TreeSet<String> usedStores) {
        StoreSpec storeSpec = spec.stores().get(store);
        if (storeSpec == null) {
            problems.add(at + "no store [" + store + "]");
            return;
        }
        usedStores.add(store);
        // A caller with no value left may make the reservation itself, bounded by the store timeout only (04 §2.1).
        if (storeSpec.timeout().compareTo(batch.reserveTimeout()) > 0) {
            problems.add(at + "store [" + store + "] timeout " + storeSpec.timeout() + " exceeds batch.reserveTimeout "
                + batch.reserveTimeout());
        }
    }

    private static void checkMachine(RuntimeSpec spec, MachineSpec machine, String at, List<String> problems, TreeSet<String> usedStores) {
        if (machine.bits() < 1 || machine.bits() > MAX_MACHINE_BITS) {
            problems.add(at + "bits must be in [1, " + MAX_MACHINE_BITS + "]: " + machine.bits());
            return;
        }
        switch (machine) {
            case MachineSpec.Leased leased -> {
                StoreSpec storeSpec = spec.stores().get(leased.store());
                if (storeSpec == null) {
                    problems.add(at + "no store [" + leased.store() + "]");
                } else {
                    usedStores.add(leased.store());
                }
                if (!isPositive(leased.ttl())) {
                    problems.add(at + "ttl must be positive: " + leased.ttl());
                }
                if (leased.tolerance().isNegative()) {
                    problems.add(at + "tolerance must not be negative: " + leased.tolerance());
                }
                if (leased.slotOffset() < 0) {
                    problems.add(at + "slotOffset must not be negative: " + leased.slotOffset());
                }
                if (leased.acquireTimeout().isNegative()) {
                    problems.add(at + "acquireTimeout must not be negative: " + leased.acquireTimeout());
                }
                leased.preferredSlot().ifPresent(slot -> checkSlot(at, "preferredSlot", slot, machine.bits(), problems));
                leased.ownerKey().filter(String::isBlank)
                    .ifPresent(key -> problems.add(at + "ownerKey must not be blank"));
            }
            case MachineSpec.Static fixed -> {
                checkSlot(at, "slot", fixed.slot(), machine.bits(), problems);
                if (fixed.ahead().toMillis() < 2) {
                    problems.add(at + "ahead must be at least 2ms: " + fixed.ahead());
                }
            }
        }
    }

    private static void checkSlot(String at, String what, int slot, int bits, List<String> problems) {
        if (slot < 0 || slot >= 1 << bits) {
            problems.add(at + what + " must be in [0, " + (1 << bits) + "): " + slot);
        }
    }

    private static boolean isPositive(Duration duration) {
        return !duration.isNegative() && !duration.isZero();
    }

    /**
     * Derives the owner key (07-runtime-and-spring.md §1.1): the configured key, else {@code host:port}, else
     * {@code host:pid}.
     *
     * @param configured the configured owner key
     * @param host       the host resolver
     * @param port       the port
     * @return the owner key
     */
    public static String deriveOwnerKey(Optional<String> configured, HostResolver host, OptionalInt port) {
        if (configured.isPresent()) {
            return configured.get();
        }
        String suffix = port.isPresent() ? Integer.toString(port.getAsInt()) : Long.toString(ProcessHandle.current().pid());
        return host.host() + ':' + suffix;
    }
}
