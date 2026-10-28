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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A {@link RuntimeSpec} checked as a whole before anything starts (07-runtime-and-spring.md §1): every problem is
 * reported at once, and the codecs are built. Only the stores the enabled generators and the machine slot use are
 * opened, and the machine slot only when a time-based generator is enabled.
 */
public final class RuntimePlan {
    /** The slot bits a lease keeper accepts. */
    static final int MAX_MACHINE_BITS = Integer.SIZE - 2;

    private final List<Generator> generators;
    private final List<String> stores;
    private final Optional<MachineSpec> machine;
    private final Optional<String> ownerKey;

    private RuntimePlan(List<Generator> generators, List<String> stores, Optional<MachineSpec> machine, Optional<String> ownerKey) {
        this.generators = List.copyOf(generators);
        this.stores = List.copyOf(stores);
        this.machine = machine;
        this.ownerKey = ownerKey;
    }

    /**
     * A generator to create.
     *
     * @param name  the name
     * @param spec  the spec; a {@link GeneratorSpec.Disabled} one is not created
     * @param codec the {@code IdCodec}, {@code PartsCodec} or {@code GroupedCodec} by strategy; null when disabled
     * @param ids   the user generator of a {@link GeneratorSpec.Custom} (resolved) or {@link GeneratorSpec.Instance}; null
     *              otherwise
     */
    public record Generator(String name, GeneratorSpec spec, Object codec, IdGenerator ids) {
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
     * The machine slot, when a time-based generator is enabled.
     *
     * @return the machine spec
     */
    public Optional<MachineSpec> machine() {
        return machine;
    }

    /**
     * The owner key of a leased slot (§1.1).
     *
     * @return the owner key, empty without a leased slot
     */
    public Optional<String> ownerKey() {
        return ownerKey;
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
        List<String> timeBased = new ArrayList<>();
        for (Map.Entry<String, GeneratorSpec> entry : new TreeMap<>(spec.generators()).entrySet()) {
            String name = entry.getKey();
            GeneratorSpec generator = entry.getValue();
            if (name.isBlank() || name.indexOf('/') >= 0) {
                problems.add("generator [" + name + "]: the name must not be blank nor contain '/'");
                continue;
            }
            if (generator instanceof GeneratorSpec.Disabled) {
                generators.add(new Generator(name, generator, null, null));
                continue;
            }
            String at = "generator [" + name + "]: ";
            Object codec = null;
            IdGenerator ids = null;
            try {
                switch (generator) {
                    case GeneratorSpec.Snowflake snowflake -> {
                        timeBased.add(name);
                        Layout layout = snowflake.layout();
                        if (layout.totalBits() > Layout.LONG_BITS) {
                            problems.add(at + "a snowflake layout must fit a long, not " + layout.totalBits() + " bits: use cosid80");
                        }
                        checkNodeBits(spec.machine(), layout, at, problems);
                        checkEpoch(layout, options, at, problems);
                        codec = codecs.ids(snowflake.codec(), Optional.of(layout));
                    }
                    case GeneratorSpec.CosId80 cosId80 -> {
                        timeBased.add(name);
                        Layout layout = cosId80.layout();
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
                        checkNodeBits(spec.machine(), layout, at, problems);
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
            generators.add(new Generator(name, generator, codec, ids));
        }

        Optional<MachineSpec> machine = Optional.empty();
        Optional<String> ownerKey = Optional.empty();
        if (!timeBased.isEmpty()) {
            if (spec.machine().isEmpty()) {
                problems.add("machine: time-based generators " + timeBased + " need a machine slot, but the spec has no machine");
            } else {
                machine = spec.machine();
                checkMachine(spec, machine.get(), problems, usedStores);
                if (machine.get() instanceof MachineSpec.Leased leased) {
                    ownerKey = Optional.of(deriveOwnerKey(leased.ownerKey(), options.hostResolver(), options.port()));
                }
            }
        }

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
        return new RuntimePlan(generators, new ArrayList<>(usedStores), machine, ownerKey);
    }

    private static void checkNodeBits(Optional<MachineSpec> machine, Layout layout, String at, List<String> problems) {
        if (machine.isPresent() && layout.nodeBits() < machine.get().bits()) {
            problems.add(at + "the layout has " + layout.nodeBits() + " node bits, fewer than machine.bits " + machine.get().bits());
        }
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

    private static void checkMachine(RuntimeSpec spec, MachineSpec machine, List<String> problems, TreeSet<String> usedStores) {
        if (machine.bits() < 1 || machine.bits() > MAX_MACHINE_BITS) {
            problems.add("machine: bits must be in [1, " + MAX_MACHINE_BITS + "]: " + machine.bits());
            return;
        }
        switch (machine) {
            case MachineSpec.Leased leased -> {
                StoreSpec storeSpec = spec.stores().get(leased.store());
                if (storeSpec == null) {
                    problems.add("machine: no store [" + leased.store() + "]");
                } else {
                    usedStores.add(leased.store());
                }
                if (!isPositive(leased.ttl())) {
                    problems.add("machine: ttl must be positive: " + leased.ttl());
                }
                if (leased.tolerance().isNegative()) {
                    problems.add("machine: tolerance must not be negative: " + leased.tolerance());
                }
                if (leased.acquireTimeout().isNegative()) {
                    problems.add("machine: acquireTimeout must not be negative: " + leased.acquireTimeout());
                }
                leased.preferredSlot().ifPresent(slot -> checkSlot("preferredSlot", slot, machine.bits(), problems));
                leased.ownerKey().filter(String::isBlank)
                    .ifPresent(key -> problems.add("machine: ownerKey must not be blank"));
            }
            case MachineSpec.Static fixed -> {
                checkSlot("slot", fixed.slot(), machine.bits(), problems);
                if (fixed.ahead().toMillis() < 2) {
                    problems.add("machine: ahead must be at least 2ms: " + fixed.ahead());
                }
            }
        }
    }

    private static void checkSlot(String what, int slot, int bits, List<String> problems) {
        if (slot < 0 || slot >= 1 << bits) {
            problems.add("machine: " + what + " must be in [0, " + (1 << bits) + "): " + slot);
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
