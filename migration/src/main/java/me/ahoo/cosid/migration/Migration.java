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

package me.ahoo.cosid.migration;

import me.ahoo.cosid.migration.config.ConfigConverter;
import me.ahoo.cosid.migration.config.FlatConfig;
import me.ahoo.cosid.migration.config.Legacy4x;
import me.ahoo.cosid.migration.source.SourceReader;
import me.ahoo.cosid.migration.source.Sources;
import me.ahoo.cosid.migration.target.NamespaceState;
import me.ahoo.cosid.migration.target.RangeState;
import me.ahoo.cosid.migration.target.TargetAdmin;
import me.ahoo.cosid.migration.target.Targets;
import me.ahoo.cosid.time.Layout;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The commands of 09 §3, without the command line: {@link Main} parses arguments into the option records here, tests
 * call the commands directly. Stores are opened through replaceable factories, so tests can inject faults.
 */
public final class Migration {
    /** Default {@code --seal-gap}: 2^40 (09 §2). */
    public static final long DEFAULT_SEAL_GAP = 1L << 40;
    /** Default {@code --source-skew}: the 4.x clock skew limit (09 §4.2). */
    public static final Duration DEFAULT_SOURCE_SKEW = Duration.ofSeconds(10);
    /** Default {@code --observe} (09 §2). */
    public static final Duration DEFAULT_OBSERVE = Duration.ofMinutes(2);
    /** The {@code T4 + 1s} of W: 4.x writes its local state once a second (09 §4.2). */
    static final long STATE_LAG_MILLIS = 1000;

    private final Function<StoreLocation, SourceReader> sources;
    private final Function<StoreLocation, TargetAdmin> targets;
    private final Sleeper sleeper;
    private final PrintStream out;

    public Migration(PrintStream out) {
        this(Sources::open, Targets::open, Sleeper.THREAD, out);
    }

    public Migration(Function<StoreLocation, SourceReader> sources, Function<StoreLocation, TargetAdmin> targets, Sleeper sleeper, PrintStream out) {
        this.sources = sources;
        this.targets = targets;
        this.sleeper = sleeper;
        this.out = out;
    }

    /** How the quiet-period check waits. */
    @FunctionalInterface
    public interface Sleeper {
        Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());

        void sleep(Duration duration) throws InterruptedException;
    }

    /**
     * {@code plan} options.
     *
     * @param from          the 4.x store
     * @param to            the 5.0 store
     * @param machineFrom   the 4.x store of the machine ids when not {@code from} ({@code --machine-from}); null otherwise
     * @param machineTo     the 5.0 store of the slots when not {@code to} ({@code --machine-to}); null otherwise
     * @param namespace     the namespace
     * @param layoutConfig  the 4.x configuration, optional
     * @param targetConfig  the 5.0 configuration, optional: layouts are compared with it instead of the converted ones
     * @param slotBits      the slot bits, null for the 4.x machine bits (10 by default)
     * @param tolerance     the tolerance of the 5.0 namespace
     * @param sourceSkew    the source skew of the W estimate
     * @param renames       4.x to 5.0 generator names
     * @param groupPatterns 5.0 generator name to the 4.x group key pattern
     * @param output        where {@code plan.json} goes
     * @param slotOffset    the slot offset of a shared {@code cosid.generator.namespace} ({@code --slot-offset}); null for
     *                      the top of the nodes, {@code 2^nodeBits - 2^bits}; 0 migrates it at once, every service
     *                      sharing it stopped (api-freeze.md §2.7)
     */
    public record PlanOptions(StoreLocation from, StoreLocation to, StoreLocation machineFrom, StoreLocation machineTo, String namespace,
                              Path layoutConfig, Path targetConfig, Integer slotBits, Duration tolerance, Duration sourceSkew,
                              Map<String, String> renames, Map<String, String> groupPatterns, Path output, Integer slotOffset) {
        /** With the recommended slot offset. */
        public PlanOptions(StoreLocation from, StoreLocation to, StoreLocation machineFrom, StoreLocation machineTo, String namespace,
                           Path layoutConfig, Path targetConfig, Integer slotBits, Duration tolerance, Duration sourceSkew,
                           Map<String, String> renames, Map<String, String> groupPatterns, Path output) {
            this(from, to, machineFrom, machineTo, namespace, layoutConfig, targetConfig, slotBits, tolerance, sourceSkew, renames, groupPatterns, output,
                null);
        }

        /** Without separate machine stores. */
        public PlanOptions(StoreLocation from, StoreLocation to, String namespace, Path layoutConfig, Path targetConfig, Integer slotBits,
                           Duration tolerance, Duration sourceSkew, Map<String, String> renames, Map<String, String> groupPatterns, Path output) {
            this(from, to, null, null, namespace, layoutConfig, targetConfig, slotBits, tolerance, sourceSkew, renames, groupPatterns, output);
        }
    }

    /**
     * Makes a plan: read-only on both stores.
     *
     * @param options the options
     * @return the plan, also written to {@code options.output()}
     * @throws MigrationAbortedException when the plan would be unsafe: the share without {@code --layout-config}, or
     *                                   4.x stores the locations do not match, or 4.x CosIdGenerator bits 5.0 cannot
     *                                   keep, or a slot offset of a shared namespace that does not fit
     */
    public Plan plan(PlanOptions options) {
        List<String> warnings = new ArrayList<>();
        Legacy4x legacy = options.layoutConfig() == null ? null : new Legacy4x(FlatConfig.load(options.layoutConfig()));
        // The plan of the shared namespace of the CosIdGenerator itself (api-freeze.md §2.7): machine records only.
        boolean sharedPlan = legacy != null && legacy.sharedGeneratorNamespace().filter(options.namespace()::equals).isPresent();
        Map<String, String> renames = new TreeMap<>();
        Map<String, String> groupPatterns = new TreeMap<>();
        Map<String, List<String>> extraNamespaces = new TreeMap<>();
        if (legacy != null) {
            legacy.cosidLayoutProblem().ifPresent(reason -> {
                throw new MigrationAbortedException(reason);
            });
            if (!sharedPlan) {
                renames.putAll(legacy.renames());
                groupPatterns.putAll(legacy.groupPatterns());
                legacy.extraNamespaces().forEach((ns, names) -> extraNamespaces.put(ns, List.copyOf(names)));
                if (!legacy.namespace().equals(options.namespace())) {
                    warnings.add("--layout-config has namespace [" + legacy.namespace() + "], the plan [" + options.namespace() + "]");
                }
            }
            checkStores(legacy, options);
        }
        renames.putAll(options.renames());
        groupPatterns.putAll(options.groupPatterns());
        final int bits = options.slotBits() != null ? options.slotBits() : legacy != null ? legacy.machineBit() : Legacy4x.DEFAULT_MACHINE_BIT;
        if (options.slotOffset() != null && !sharedPlan) {
            throw new MigrationAbortedException("--slot-offset applies to the shared cosid.generator.namespace of --layout-config only: plan it with"
                + " --namespace <that namespace>");
        }

        // The shared namespace of this service's CosIdGenerator, as the target records it: migrated once before (§2.7).
        Plan.GeneratorNamespace generatorNamespace = null;
        if (legacy != null && !sharedPlan && legacy.sharedGeneratorNamespace().isPresent()) {
            String shared = legacy.sharedGeneratorNamespace().get();
            try (TargetAdmin target = targets.apply(options.to()); TargetAdmin slotTarget = machineTarget(options.to(), options.machineTo(), target)) {
                generatorNamespace = slotTarget.namespace(shared)
                    .map(state -> new Plan.GeneratorNamespace(shared, state.limit(), state.toleranceMillis(), state.slotOffset()))
                    .orElse(new Plan.GeneratorNamespace(shared, 0, 0, 0));
            }
            if (!generatorNamespace.prepared()) {
                warnings.add("cosid.generator.namespace = " + shared + " is not migrated yet: migrate it first, once for every service sharing it"
                    + " (plan and apply --namespace " + shared + " with this --layout-config); apply refuses until then");
            } else if (generatorNamespace.slotOffset() > 0) {
                // A rolling migration: 4.x keeps issuing in the shared namespace, its records count for nothing here.
                if (extraNamespaces.getOrDefault(shared, List.of()).isEmpty()) {
                    extraNamespaces.remove(shared);
                }
                warnings.add("cosid.generator.namespace = " + shared + " was migrated with slot offset " + generatorNamespace.slotOffset()
                    + ": config writes cosid.machines.generator, and 4.x instances of other services may keep running");
            } else {
                warnings.add("cosid.generator.namespace = " + shared + " was migrated with slot offset 0: its machine records count for T4 and the"
                    + " quiet period, and 4.x instances of other services that share it must be stopped too");
            }
        } else if (legacy != null && !sharedPlan) {
            legacy.generatorNamespace().ifPresent(shared -> warnings.add("cosid.generator.namespace = " + shared + " (the CosIdGenerator is disabled):"
                + " the machine records of [" + shared + "] count for T4 and the quiet period"));
        }

        LegacySnapshot snapshot;
        long sourceNow;
        try (Readers readers = readers(options.from(), options.machineFrom())) {
            snapshot = readers.read(options.namespace(), extraNamespaces);
            sourceNow = readers.now();
        }
        Plan.Shared shared = null;
        int slotOffset = 0;
        if (sharedPlan) {
            int maxMachineId = snapshot.maxMachineId(options.namespace());
            int nodeBits = legacy.cosidLayout().nodeBits();
            // The 4.x CosIdGenerator distributes with cosid.generator.machine-bit, which 5.0 keeps as node-bits; 4.1.0 throws
            // MachineIdOverflowException above maxMachineId(machineBit), so only an offset at this cap would be safe by construction.
            final long legacyCap = 1L << nodeBits;
            int recommended = defaultSlotOffset(bits, nodeBits);
            slotOffset = options.slotOffset() == null ? recommended : options.slotOffset();
            checkSlotOffset(options.namespace(), maxMachineId, slotOffset, bits, nodeBits);
            boolean rollingOffset = slotOffset > 0;
            long headroom = rollingOffset ? slotOffset - (maxMachineId + 1L) : 0;
            boolean guaranteed = !rollingOffset || slotOffset >= legacyCap;
            shared = new Plan.Shared(maxMachineId, recommended, nodeBits, rollingOffset, legacyCap, headroom, guaranteed);
            warnings.add(sharedWarning(options.namespace(), shared, slotOffset, bits));
        }
        Plan.Slots slots = Plan.Slots.of(bits, options.tolerance().toMillis(), slotOffset);
        final boolean rolling = shared != null && shared.rolling();
        boolean share = snapshot.segments().keySet().stream().anyMatch(key -> key.name().equals(Legacy4x.SHARE) || key.name().startsWith(Legacy4x.SHARE + "@"));
        if (share && legacy == null && !options.renames().containsKey(Legacy4x.SHARE)) {
            throw new MigrationAbortedException("4.x key [" + options.namespace() + "." + Legacy4x.SHARE + "] needs --layout-config (or --rename "
                + Legacy4x.SHARE + "=<generator>): its 5.0 name depends on whether the 4.x snowflake share is enabled too");
        }
        KeyMapper mapper = new KeyMapper(options.namespace(), renames, groupPatterns);
        List<String> unrecognized = new ArrayList<>(snapshot.unrecognized());
        List<Plan.SegmentPlan> segments = new ArrayList<>();
        long targetNow;
        try (TargetAdmin target = targets.apply(options.to()); TargetAdmin slotTarget = machineTarget(options.to(), options.machineTo(), target)) {
            targetNow = Math.max(target.now(), slotTarget.now());
            Map<String, LegacySnapshot.Key> owners = new LinkedHashMap<>();
            for (Map.Entry<LegacySnapshot.Key, Long> entry : snapshot.segments().entrySet()) {
                LegacySnapshot.Key key = entry.getKey();
                KeyMapper.Mapping mapping = mapper.map(key.name());
                if (!mapping.mapped()) {
                    unrecognized.add(key + ": " + mapping.problem());
                    continue;
                }
                LegacySnapshot.Key other = owners.putIfAbsent(mapping.targetKey(), key);
                if (other != null) {
                    warnings.add("4.x segments [" + other + "] and [" + key + "] both map to [" + mapping.targetKey() + "]: the larger value wins");
                }
                Long targetNext = target.range(mapping.targetKey()).map(RangeState::next).orElse(null);
                segments.add(new Plan.SegmentPlan(key.namespace(), key.name(), mapping.generator(), mapping.period(), mapping.targetKey(), entry.getValue(),
                    next(key.toString(), entry.getValue(), 0), targetNext));
                if (key.name().contains(".") && legacy == null) {
                    warnings.add("4.x key [" + key + "] may belong to a namespace [" + key.namespace() + "." + key.name().substring(0, key.name().indexOf('.'))
                        + "]: give --layout-config so plan knows the namespaces in use");
                }
            }
            slotTarget.namespace(options.namespace()).ifPresent(state -> {
                if (!TargetAdmin.matches(state, slots.limit(), slots.toleranceMillis(), slots.slotOffset())) {
                    warnings.add("the 5.0 namespace is recorded with limit " + state.limit() + ", tolerance " + state.toleranceMillis()
                        + "ms and slot offset " + state.slotOffset() + ": apply fails unless --slot-bits / --tolerance / --slot-offset match");
                }
                if (state.live() > 0 && !rolling) {
                    warnings.add("the 5.0 namespace has " + state.live() + " live slot lease(s): stop 5.0 before apply");
                }
            });
        }
        if (!unrecognized.isEmpty()) {
            warnings.add("apply aborts unless each key not migrated is named with --allow-unmigrated <key>");
        }
        long sourceSkew = options.sourceSkew().toMillis();
        long w = floorW(Math.max(targetNow, sourceNow), snapshot.maxTimestamp(), sourceSkew);
        List<LayoutCheck> layouts = layouts(legacy, options.targetConfig(), w);
        if (legacy == null) {
            warnings.add("no --layout-config: layouts are not validated (M3), namespaces of their own and separate machine stores are not"
                + " checked, group patterns default to the 5.0 presets");
        }
        Plan plan = new Plan(Plan.FORMAT, Instant.now().toString(), options.namespace(), options.from().redacted(), options.to().redacted(),
            options.machineFrom() == null ? null : options.machineFrom().redacted(), options.machineTo() == null ? null : options.machineTo().redacted(),
            extraNamespaces, slots, renames, groupPatterns, segments, unrecognized,
            new Plan.Machines(snapshot.machines().size(), snapshot.maxTimestamp(), Math.max(targetNow, sourceNow), sourceSkew, w,
                snapshot.maxMachineId(options.namespace())), layouts, warnings, shared, generatorNamespace);
        Json.write(options.output(), plan);
        report(plan);
        return plan;
    }

    /**
     * The 4.x stores of {@code --layout-config} against the locations (rc-review D-B4): segments in {@code --from}, and
     * machine ids in {@code --from} too or in {@code --machine-from}; slots of a separate 4.x machine store go to the
     * 5.0 store of {@code cosid.machine.leased}, which {@code --machine-to} names.
     */
    /** The smallest power of two at least {@code n}, {@code n >= 1}. */
    static int roundUpToPowerOfTwo(int n) {
        return Integer.bitCount(n) == 1 ? n : Integer.highestOneBit(n) << 1;
    }

    /**
     * The default slot offset of a shared namespace (api-freeze.md §2.7): the 5.0 slots at the top of the nodes,
     * {@code 2^nodeBits - 2^bits}, so 4.x keeps the most room below them.
     *
     * @param bits     the slot bits
     * @param nodeBits the node bits of the 5.0 CosIdGenerator
     * @return the offset, at most what an {@code int} node holds
     */
    static int defaultSlotOffset(int bits, int nodeBits) {
        long top = Math.min(1L << nodeBits, 1L << Integer.SIZE - 1);
        return (int) Math.max(0, top - (1L << bits));
    }

    /**
     * What a shared namespace plan says about its slot offset: the headroom 4.x has left below it, loudly when it is
     * less than the 5.0 slots.
     */
    static String sharedWarning(String namespace, Plan.Shared shared, int slotOffset, int bits) {
        String prefix = "[" + namespace + "] is the shared cosid.generator.namespace, ";
        if (!shared.rolling()) {
            return prefix + "migrated at once (slot offset 0): every 4.x instance of every service sharing it must be stopped during apply, and its"
                + " quiet period covers them";
        }
        if (shared.guaranteed()) {
            return prefix + "migrated rolling with slot offset " + slotOffset + ", at or above the hard cap " + shared.legacyCap()
                + " of the 4.x distributor: 4.x never reaches the 5.0 nodes";
        }
        String text = prefix + "migrated rolling with slot offset " + slotOffset + ": the 4.x machine ids are below " + (shared.maxMachineId() + 1)
            + ", so 4.x can hand out " + shared.headroom() + " more machine ids (its hard cap is " + shared.legacyCap() + ") before its nodes meet"
            + " 5.0's; it hands out the lowest free id, so keep fewer than " + slotOffset + " 4.x instances (surge included) until every service moved."
            + " Nothing enforces it; verify checks it afterwards";
        return shared.headroom() < 1L << bits ? "LOW HEADROOM: " + text + ". The headroom is below the 2^" + bits + " = " + (1L << bits)
            + " 5.0 slots: raise --slot-offset or lower --slot-bits" : text;
    }

    /**
     * The slot offset of a shared namespace (api-freeze.md §2.7): above every 4.x machine id, and the slots
     * {@code [offset, offset + 2^bits)} within the node bits of the CosIdGenerator.
     */
    static void checkSlotOffset(String namespace, int maxMachineId, int slotOffset, int bits, int nodeBits) {
        if (slotOffset > 0 && slotOffset <= maxMachineId) {
            throw new MigrationAbortedException("--slot-offset " + slotOffset + " is not above the 4.x machine ids of [" + namespace + "] (the largest is "
                + maxMachineId + "): 5.0 nodes would collide with 4.x ones; take " + roundUpToPowerOfTwo(maxMachineId + 1) + " or more, or 0 to"
                + " migrate at once with every service stopped");
        }
        long end = (long) slotOffset + (1L << bits);
        if (end > 1L << nodeBits) {
            int fitting = 63 - Long.numberOfLeadingZeros((1L << nodeBits) - slotOffset);
            throw new MigrationAbortedException("slot offset " + slotOffset + " + 2^" + bits + " slots = " + end + " exceeds the 2^" + nodeBits
                + " nodes of the CosIdGenerator (cosid.generator.machine-bit = " + nodeBits + ")"
                + (fitting >= 1 && (long) slotOffset + (1L << fitting) <= 1L << nodeBits ? ": take --slot-bits " + fitting + " or fewer" : ": no slots fit above it")
                + ", or migrate at once with --slot-offset 0 and every service stopped");
        }
    }

    private static void checkStores(Legacy4x legacy, PlanOptions options) {
        legacy.segmentStoreType().filter(type -> !type.equals(options.from().type())).ifPresent(type -> {
            throw new MigrationAbortedException("--layout-config keeps the 4.x segments in " + type + " (cosid.segment.distributor.type), --from is "
                + options.from().type());
        });
        Optional<String> machine = legacy.machineStoreType();
        if (machine.isEmpty()) {
            return;
        }
        StoreLocation machineFrom = options.machineFrom() == null ? options.from() : options.machineFrom();
        if (!machine.get().equals(machineFrom.type())) {
            throw new MigrationAbortedException("--layout-config keeps the 4.x machine ids in " + machine.get() + " (cosid.machine.distributor.type), "
                + (options.machineFrom() == null ? "not in --from (" + options.from().type() + "): give --machine-from" : "--machine-from is " + machineFrom.type()));
        }
        boolean separate = legacy.segmentStoreType().isPresent() && !legacy.segmentStoreType().get().equals(machine.get());
        if (separate && options.machineTo() == null) {
            throw new MigrationAbortedException("4.x keeps segments (" + legacy.segmentStoreType().get() + ") and machine ids (" + machine.get()
                + ") in separate stores, and config writes cosid.machine.leased on the " + machine.get()
                + " store: give --machine-to, the 5.0 store of the slots (it may be --to)");
        }
    }

    /** The 4.x readers of one command: one reader when segments and machine ids share a store. */
    private Readers readers(StoreLocation from, StoreLocation machineFrom) {
        SourceReader segments = sources.apply(from);
        try {
            return new Readers(segments, machineFrom == null ? segments : sources.apply(machineFrom));
        } catch (RuntimeException e) {
            segments.close();
            throw e;
        }
    }

    private record Readers(SourceReader segments, SourceReader machines) implements AutoCloseable {
        LegacySnapshot read(String namespace, Map<String, List<String>> extra) {
            Map<String, Set<String>> names = new TreeMap<>();
            extra.forEach((ns, list) -> names.put(ns, new TreeSet<>(list)));
            return LegacySnapshot.read(segments, machines, namespace, names);
        }

        long now() {
            return machines == segments ? segments.now() : Math.max(segments.now(), machines.now());
        }

        @Override
        public void close() {
            try {
                segments.close();
            } finally {
                if (machines != segments) {
                    machines.close();
                }
            }
        }
    }

    /** The target of the slots: a separate one, closed with the command, or the range target itself, never closed twice. */
    private TargetAdmin machineTarget(StoreLocation to, StoreLocation machineTo, TargetAdmin target) {
        if (machineTo == null) {
            return new Unclosed(target);
        }
        return targets.apply(machineTo);
    }

    /** A target the caller closes elsewhere. */
    private record Unclosed(TargetAdmin delegate) implements TargetAdmin {
        @Override
        public long now() {
            return delegate.now();
        }

        @Override
        public Optional<RangeState> range(String key) {
            return delegate.range(key);
        }

        @Override
        public RangeState raise(String key, long next) {
            return delegate.raise(key, next);
        }

        @Override
        public List<RangeState> ranges(String prefix) {
            return delegate.ranges(prefix);
        }

        @Override
        public boolean delete(String key) {
            return delegate.delete(key);
        }

        @Override
        public Optional<NamespaceState> namespace(String namespace) {
            return delegate.namespace(namespace);
        }

        @Override
        public NamespaceState prepare(String namespace, int limit, long toleranceMillis, int slotOffset, long nextFloor) {
            return delegate.prepare(namespace, limit, toleranceMillis, slotOffset, nextFloor);
        }

        @Override
        public void close() {
        }
    }

    private void report(Plan plan) {
        out.println("Plan for namespace [" + plan.namespace() + "]: " + plan.source() + " -> " + plan.target()
            + (plan.machineSource() == null ? "" : "; machine ids " + plan.machineSource()) + (plan.machineTarget() == null ? "" : " -> slots " + plan.machineTarget()));
        for (Plan.SegmentPlan segment : plan.segments()) {
            out.println("  segment " + segment.sourceNamespace() + "." + segment.sourceName() + " = " + segment.lastMaxId() + "  ->  " + segment.targetKey()
                + " next >= " + segment.next() + (segment.targetNext() == null ? "" : " (target has " + segment.targetNext() + ")"));
        }
        for (String key : plan.unrecognized()) {
            out.println("  NOT MIGRATED " + key);
        }
        Plan.Machines machines = plan.machines();
        out.println("  machine records: " + machines.records() + ", T4 = " + machines.maxTimestamp() + "; W estimate = " + machines.estimatedW()
            + " (" + Instant.ofEpochMilli(machines.estimatedW()) + ")");
        out.println("  slots: limit " + plan.slots().limit() + ", tolerance " + plan.slots().toleranceMillis() + "ms, slot offset " + plan.slots().slotOffset()
            + ", next floor W");
        if (plan.shared() != null) {
            out.println("  shared cosid.generator.namespace: 4.x machine ids up to " + plan.shared().maxMachineId() + ", 4.x hard cap "
                + plan.shared().recommendedOffset() + ", node bits " + plan.shared().nodeBits() + (plan.shared().rolling() ? "; rolling" : "; at once")
                + "; headroom " + plan.shared().headroom()
                + (plan.shared().guaranteed() ? " (guaranteed)" : " (4.x must stay below the slot offset)"));
        }
        if (plan.generatorNamespace() != null) {
            out.println("  cosid.generator.namespace " + plan.generatorNamespace().namespace() + ": "
                + (plan.generatorNamespace().prepared() ? "migrated, slot offset " + plan.generatorNamespace().slotOffset() : "NOT migrated yet"));
        }
        for (LayoutCheck layout : plan.layouts()) {
            out.println("  layout " + layout.generator() + ": " + (layout.ok() ? "OK" : "FAIL") + " - " + layout.reason());
        }
        for (String warning : plan.warnings()) {
            out.println("  WARNING " + warning);
        }
    }

    static long next(String name, long lastMaxId, long gap) {
        if (lastMaxId > Long.MAX_VALUE - 1 - gap) {
            throw new IllegalStateException("Segment [" + name + "] at " + lastMaxId + " has no room left for the gap " + gap);
        }
        return lastMaxId + 1 + gap;
    }

    /**
     * {@code W = max(T_apply, T4 + 1s) + sourceSkew} (09 §4.2).
     *
     * @param targetNow    T_apply, the target store clock
     * @param maxTimestamp T4
     * @param sourceSkew   the source skew
     * @return W
     */
    public static long floorW(long targetNow, long maxTimestamp, long sourceSkew) {
        return Math.max(targetNow, maxTimestamp + STATE_LAG_MILLIS) + sourceSkew;
    }

    static List<LayoutCheck> layouts(Legacy4x legacy, Path targetConfig, long w) {
        if (legacy == null) {
            return List.of();
        }
        Map<String, Layout> converted = ConfigConverter.targetLayouts(legacy);
        Map<String, Layout> target = targetConfig == null ? converted : ConfigConverter.layoutsOf5(FlatConfig.load(targetConfig));
        List<LayoutCheck> checks = new ArrayList<>();
        legacy.layouts().forEach((name, old) -> {
            Layout neu = target.get(name);
            if (neu == null) {
                checks.add(new LayoutCheck(name, LayoutCheck.describe(old), "(none)", true, "no 5.0 time-based generator of this name"));
            } else {
                checks.add(LayoutCheck.check(name, old, neu, w));
            }
        });
        return checks;
    }

    /**
     * {@code apply} options.
     *
     * @param plan              the plan file
     * @param confirmStopped    {@code --confirm-stopped}: the operator stopped every 4.x instance
     * @param observe           the quiet period; zero turns the check off
     * @param gap               added to every segment's next value
     * @param sourceSkew        the source skew of W
     * @param sealSource        whether to seal the source (on unless {@code --no-seal-source})
     * @param sealGap           how far sealing advances a 4.x segment
     * @param result            where {@code apply-result.json} goes
     * @param fromOptions       options of the 4.x store not kept in the plan, such as the password
     * @param toOptions         options of the 5.0 store not kept in the plan
     * @param layoutConfig      the 4.x configuration, to check the layouts again with the real W; optional
     * @param targetConfig      the 5.0 configuration of that check; optional
     * @param allowUnmigrated   4.x keys that may stay unmigrated ({@code --allow-unmigrated}); any other aborts
     * @param machineFromOptions options of the 4.x machine store not kept in the plan
     * @param machineToOptions  options of the 5.0 slot store not kept in the plan
     */
    public record ApplyOptions(Path plan, boolean confirmStopped, Duration observe, long gap, Duration sourceSkew, boolean sealSource, long sealGap,
                               Path result, Map<String, String> fromOptions, Map<String, String> toOptions, Path layoutConfig, Path targetConfig,
                               Set<String> allowUnmigrated, Map<String, String> machineFromOptions, Map<String, String> machineToOptions) {
        /** Without keys allowed unmigrated or separate machine stores. */
        public ApplyOptions(Path plan, boolean confirmStopped, Duration observe, long gap, Duration sourceSkew, boolean sealSource, long sealGap,
                            Path result, Map<String, String> fromOptions, Map<String, String> toOptions, Path layoutConfig, Path targetConfig) {
            this(plan, confirmStopped, observe, gap, sourceSkew, sealSource, sealGap, result, fromOptions, toOptions, layoutConfig, targetConfig,
                Set.of(), Map.of(), Map.of());
        }
    }

    /**
     * Applies a plan (09 §2, §3): idempotent, values only ever increase.
     *
     * <ol>
     *     <li>Snapshots the source, waits the quiet period, snapshots again; any change aborts, so does a key that is
     *     not migrated unless {@code --allow-unmigrated} names it.</li>
     *     <li>Seals each 4.x segment with one atomic increment of {@code sealGap}, whose result bounds everything 4.x
     *     reserved before it; a segment already at {@code planned + sealGap} or beyond was sealed by an earlier run.</li>
     *     <li>Raises each 5.0 key to {@code lastMaxId + 1 + gap} (M1).</li>
     *     <li>Records the slot metadata and raises every slot's next floor to W (M2).</li>
     * </ol>
     *
     * @param options the options
     * @return the result, also written to {@code options.result()}
     * @throws MigrationAbortedException if a check fails before anything is written
     */
    public ApplyResult apply(ApplyOptions options) {
        if (options.gap() < 0 || options.sealGap() <= 0) {
            throw new IllegalArgumentException("--gap must not be negative and --seal-gap must be positive");
        }
        Plan plan = Json.read(options.plan(), Plan.class);
        if (!Plan.FORMAT.equals(plan.format())) {
            throw new MigrationAbortedException(options.plan() + " is no " + Plan.FORMAT + " file");
        }
        // A shared namespace migrated rolling: 4.x keeps running there, the slot offset keeps the nodes apart (§2.7).
        boolean rolling = plan.shared() != null && plan.shared().rolling();
        if (!options.confirmStopped() && !rolling) {
            throw new MigrationAbortedException("apply needs --confirm-stopped: stop every 4.x instance of the namespace first (09 §2)");
        }
        if (options.layoutConfig() != null) {
            new Legacy4x(FlatConfig.load(options.layoutConfig())).cosidLayoutProblem().ifPresent(reason -> {
                throw new MigrationAbortedException(reason);
            });
        }
        if (!plan.layoutsOk()) {
            throw new MigrationAbortedException("the plan failed the layout validation (M3): " + failed(plan.layouts()));
        }
        StoreLocation from = plan.source().withOptions(options.fromOptions()).withEnvironmentPassword("FROM");
        StoreLocation to = plan.target().withOptions(options.toOptions()).withEnvironmentPassword("TO");
        StoreLocation machineFrom = plan.machineSource() == null ? null
            : plan.machineSource().withOptions(options.machineFromOptions()).withEnvironmentPassword("MACHINE_FROM");
        StoreLocation machineTo = plan.machineTarget() == null ? null
            : plan.machineTarget().withOptions(options.machineToOptions()).withEnvironmentPassword("MACHINE_TO");
        Map<String, List<String>> extra = plan.extraNamespaces() == null ? Map.of() : plan.extraNamespaces();
        List<String> warnings = new ArrayList<>();
        Map<LegacySnapshot.Key, Plan.SegmentPlan> planned = new LinkedHashMap<>();
        plan.segments().forEach(segment -> planned.put(new LegacySnapshot.Key(segment.sourceNamespace(), segment.sourceName()), segment));
        KeyMapper mapper = new KeyMapper(plan.namespace(), plan.renames(), plan.groupPatterns());
        try (Readers source = readers(from, machineFrom); TargetAdmin target = targets.apply(to);
             TargetAdmin slotTarget = machineTarget(to, machineTo, target)) {
            final Plan.GeneratorNamespace generatorNamespace = checkGeneratorNamespace(plan, slotTarget);
            LegacySnapshot first = source.read(plan.namespace(), extra);
            long observed = 0;
            LegacySnapshot snapshot = first;
            if (rolling) {
                warnings.add("a rolling migration of the shared namespace: no quiet period, 4.x keeps running with machine ids below the slot offset "
                    + plan.slots().slotOffset() + (plan.shared().guaranteed() ? "" : " (4.x must not hand out machine ids >= "
                    + plan.slots().slotOffset() + "; verify checks it)"));
            } else if (!options.observe().isZero()) {
                out.println("Watching the 4.x store for " + options.observe() + " (--observe) ...");
                long started = System.nanoTime();
                try {
                    sleeper.sleep(options.observe());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new MigrationAbortedException("interrupted during the quiet period");
                }
                snapshot = source.read(plan.namespace(), extra);
                observed = Duration.ofNanos(System.nanoTime() - started).toMillis();
                List<String> changes = snapshot.changesSince(first);
                if (!changes.isEmpty()) {
                    throw new MigrationAbortedException("the 4.x store changed during the quiet period, a 4.x instance still runs:\n  " + String.join("\n  ", changes));
                }
            } else {
                warnings.add("the quiet-period check was off (--observe 0)");
            }
            for (LegacySnapshot.Key key : snapshot.segments().keySet()) {
                if (!planned.containsKey(key)) {
                    warnings.add("segment [" + key + "] is new since the plan: migrated as well");
                }
            }
            for (LegacySnapshot.Key key : planned.keySet()) {
                if (!snapshot.segments().containsKey(key)) {
                    throw new MigrationAbortedException("segment [" + key + "] of the plan is gone from the 4.x store");
                }
            }
            // Keys that are not migrated would start over in 5.0: refuse unless the operator names each one (rc-review D-B3).
            Set<String> unmigrated = new TreeSet<>(snapshot.unmapped());
            for (LegacySnapshot.Key key : snapshot.segments().keySet()) {
                KeyMapper.Mapping mapping = mapper.map(key.name());
                if (!mapping.mapped()) {
                    unmigrated.add(key.toString());
                    warnings.add("segment [" + key + "] is not migrated: " + mapping.problem());
                }
            }
            unmigrated.removeAll(options.allowUnmigrated());
            if (!unmigrated.isEmpty()) {
                throw new MigrationAbortedException("4.x keys would not be migrated, and 5.0 would issue their ids again: " + unmigrated
                    + "; map them (--rename, --group-pattern in plan) or name each with --allow-unmigrated <key>");
            }

            long appliedAt = Math.max(Math.max(target.now(), slotTarget.now()), source.now());
            long sourceSkew = options.sourceSkew().toMillis();
            long w = floorW(appliedAt, snapshot.maxTimestamp(), sourceSkew);
            if (options.layoutConfig() != null) {
                List<LayoutCheck> checks = layouts(new Legacy4x(FlatConfig.load(options.layoutConfig())), options.targetConfig(), w);
                if (!checks.stream().allMatch(LayoutCheck::ok)) {
                    throw new MigrationAbortedException("the layout validation (M3) fails at W = " + w + ": " + failed(checks));
                }
            }

            // 1. Seal: one atomic increment per segment; the result bounds every range 4.x reserved before it.
            Map<LegacySnapshot.Key, Long> lastMaxIds = new TreeMap<>();
            Map<LegacySnapshot.Key, Long> sealedAt = new TreeMap<>();
            for (Map.Entry<LegacySnapshot.Key, Long> entry : snapshot.segments().entrySet()) {
                LegacySnapshot.Key key = entry.getKey();
                long current = entry.getValue();
                if (!mapper.map(key.name()).mapped()) {
                    continue;
                }
                if (!options.sealSource()) {
                    lastMaxIds.put(key, current);
                    continue;
                }
                Plan.SegmentPlan segment = planned.get(key);
                if (segment != null && current - segment.lastMaxId() >= options.sealGap()) {
                    // Sealed by an earlier run of this plan: what 4.x reserved before that seal is at most current - sealGap.
                    lastMaxIds.put(key, current - options.sealGap());
                    sealedAt.put(key, current);
                    warnings.add("segment [" + key + "] was already sealed at " + current + " by an earlier apply");
                    continue;
                }
                long sealed = source.segments().seal(key.namespace(), key.name(), options.sealGap());
                lastMaxIds.put(key, sealed - options.sealGap());
                sealedAt.put(key, sealed);
            }

            // 2. Ranges (M1): next = lastMaxId + 1 + gap, only ever raised.
            Map<String, ApplyResult.SegmentResult> results = new LinkedHashMap<>();
            Map<String, Long> wanted = new LinkedHashMap<>();
            Map<String, LegacySnapshot.Key> sourceOf = new LinkedHashMap<>();
            for (Map.Entry<LegacySnapshot.Key, Long> entry : lastMaxIds.entrySet()) {
                KeyMapper.Mapping mapping = mapper.map(entry.getKey().name());
                long next = next(entry.getKey().toString(), entry.getValue(), options.gap());
                Long other = wanted.get(mapping.targetKey());
                if (other == null || next > other) {
                    wanted.put(mapping.targetKey(), next);
                    sourceOf.put(mapping.targetKey(), entry.getKey());
                }
            }
            for (Map.Entry<String, Long> entry : wanted.entrySet()) {
                RangeState state = target.raise(entry.getKey(), entry.getValue());
                if (state.sealed()) {
                    warnings.add("5.0 key [" + entry.getKey() + "] is sealed in the target: its generator will refuse it");
                }
                LegacySnapshot.Key key = sourceOf.get(entry.getKey());
                results.put(entry.getKey(), new ApplyResult.SegmentResult(key.namespace(), key.name(), entry.getKey(), lastMaxIds.get(key), entry.getValue(),
                    state.next(), sealedAt.get(key)));
            }

            // 3. Slots (M2): metadata, every slot free, next floor W.
            NamespaceState slots = slotTarget.prepare(plan.namespace(), plan.slots().limit(), plan.slots().toleranceMillis(), plan.slots().slotOffset(), w);
            ApplyResult result = new ApplyResult(ApplyResult.FORMAT, Json.sha256(options.plan()), plan.namespace(), plan.source(), plan.target(),
                plan.machineTarget(), plan.renames(), List.copyOf(new TreeSet<>(options.allowUnmigrated())), appliedAt,
                snapshot.maxTimestamp(), sourceSkew, w, Instant.ofEpochMilli(w).toString(), options.gap(), options.sealSource(), options.sealGap(),
                observed, plan.slots(), List.copyOf(results.values()), warnings, plan.shared(), generatorNamespace);
            Json.write(options.result(), result);
            out.println("Applied plan for namespace [" + plan.namespace() + "] at " + Instant.ofEpochMilli(appliedAt) + ": " + results.size()
                + " key(s), " + slots.slots() + " slot(s) with next floor >= W = " + w + " (" + result.notBefore() + ")");
            for (ApplyResult.SegmentResult segment : results.values()) {
                out.println("  " + segment.targetKey() + " next = " + segment.targetNext() + (segment.sealedAt() == null ? "" : "  (4.x sealed at " + segment.sealedAt() + ")"));
            }
            warnings.forEach(warning -> out.println("  WARNING " + warning));
            return result;
        }
    }

    /**
     * The shared namespace of the service's CosIdGenerator must be migrated before the service, as the plan saw it: its
     * metadata is what {@code config} writes as {@code cosid.machines.generator} (§2.7).
     */
    private static Plan.GeneratorNamespace checkGeneratorNamespace(Plan plan, TargetAdmin slotTarget) {
        Plan.GeneratorNamespace planned = plan.generatorNamespace();
        if (planned == null) {
            return null;
        }
        Optional<NamespaceState> state = slotTarget.namespace(planned.namespace());
        if (state.isEmpty()) {
            throw new MigrationAbortedException("the shared cosid.generator.namespace [" + planned.namespace() + "] is not migrated: migrate it first,"
                + " once for every service sharing it (plan and apply --namespace " + planned.namespace() + " with the --layout-config of a service)");
        }
        Plan.GeneratorNamespace now = new Plan.GeneratorNamespace(planned.namespace(), state.get().limit(), state.get().toleranceMillis(),
            state.get().slotOffset());
        if (!now.equals(planned)) {
            throw new MigrationAbortedException("the shared namespace [" + planned.namespace() + "] is recorded with limit " + now.limit() + ", tolerance "
                + now.toleranceMillis() + "ms and slot offset " + now.slotOffset() + " since the plan (" + planned + "): plan again");
        }
        return now;
    }

    private static String failed(List<LayoutCheck> checks) {
        return checks.stream().filter(check -> !check.ok()).map(check -> check.generator() + " (" + check.reason() + ")").toList().toString();
    }

    /**
     * {@code verify} options.
     *
     * @param plan               the plan file
     * @param result             the result file
     * @param fromOptions        options of the 4.x store not kept in the plan
     * @param toOptions          options of the 5.0 store not kept in the plan
     * @param machineFromOptions options of the 4.x machine store not kept in the plan
     * @param machineToOptions   options of the 5.0 slot store not kept in the plan
     */
    public record VerifyOptions(Path plan, Path result, Map<String, String> fromOptions, Map<String, String> toOptions,
                                Map<String, String> machineFromOptions, Map<String, String> machineToOptions) {
        /** Without separate machine stores. */
        public VerifyOptions(Path plan, Path result, Map<String, String> fromOptions, Map<String, String> toOptions) {
            this(plan, result, fromOptions, toOptions, Map.of(), Map.of());
        }
    }

    /**
     * Reads both stores again and checks M1–M3, that the source did not grow after the seal, and that no 4.x key exists
     * that the result does not have (09 §3; rc-review D-M1).
     *
     * @param options the options
     * @return the problems, empty when everything holds
     */
    public List<String> verify(VerifyOptions options) {
        Plan plan = Json.read(options.plan(), Plan.class);
        ApplyResult result = Json.read(options.result(), ApplyResult.class);
        List<String> problems = new ArrayList<>();
        if (!Json.sha256(options.plan()).equals(result.planSha256())) {
            problems.add("the result was applied from another plan (SHA-256 " + result.planSha256() + ")");
        }
        StoreLocation from = plan.source().withOptions(options.fromOptions()).withEnvironmentPassword("FROM");
        StoreLocation to = plan.target().withOptions(options.toOptions()).withEnvironmentPassword("TO");
        StoreLocation machineFrom = plan.machineSource() == null ? null
            : plan.machineSource().withOptions(options.machineFromOptions()).withEnvironmentPassword("MACHINE_FROM");
        StoreLocation machineTo = plan.machineTarget() == null ? null
            : plan.machineTarget().withOptions(options.machineToOptions()).withEnvironmentPassword("MACHINE_TO");
        int checked = 0;
        try (Readers source = readers(from, machineFrom); TargetAdmin target = targets.apply(to);
             TargetAdmin slotTarget = machineTarget(to, machineTo, target)) {
            LegacySnapshot snapshot = source.read(plan.namespace(), plan.extraNamespaces() == null ? Map.of() : plan.extraNamespaces());
            Set<LegacySnapshot.Key> applied = new HashSet<>();
            for (ApplyResult.SegmentResult segment : result.segments()) {
                checked++;
                LegacySnapshot.Key key = new LegacySnapshot.Key(segment.sourceNamespace(), segment.sourceName());
                applied.add(key);
                Optional<RangeState> range = target.range(segment.targetKey());
                if (range.isEmpty()) {
                    problems.add("M1: 5.0 key [" + segment.targetKey() + "] is missing");
                } else if (range.get().next() <= segment.lastMaxId()) {
                    problems.add("M1: 5.0 key [" + segment.targetKey() + "] is at " + range.get().next() + ", not past the 4.x value " + segment.lastMaxId());
                }
                Long now = snapshot.segments().get(key);
                long expected = segment.sealedAt() != null ? segment.sealedAt() : segment.lastMaxId();
                if (now == null) {
                    problems.add("4.x segment [" + key + "] is gone");
                } else if (now > expected) {
                    problems.add("4.x segment [" + key + "] grew to " + now + " after " + (segment.sealedAt() != null ? "the seal at " : "the migration at ")
                        + expected + ": a 4.x instance still runs" + (segment.sealedAt() != null ? " (its ids stay far from 5.0's while 5.0 is below " + segment.sealedAt() + ")" : ""));
                }
            }
            Set<String> allowed = new HashSet<>(result.allowedUnmigrated() == null ? List.of() : result.allowedUnmigrated());
            KeyMapper mapper = new KeyMapper(plan.namespace(), plan.renames(), plan.groupPatterns());
            for (LegacySnapshot.Key key : snapshot.segments().keySet()) {
                if (applied.contains(key) || allowed.contains(key.toString())) {
                    continue;
                }
                // A key the result does not have: created after the second snapshot (a 4.x instance crossing a period), or
                // one whose 5.0 key another 4.x key won. Only the former is a problem.
                KeyMapper.Mapping mapping = mapper.map(key.name());
                boolean covered = mapping.mapped() && result.segments().stream().anyMatch(segment -> segment.targetKey().equals(mapping.targetKey()));
                if (!covered) {
                    problems.add("4.x key [" + key + "] is not in the result: it was created after apply, or not migrated; 5.0 would issue its ids again");
                }
            }
            for (Plan.SegmentPlan segment : plan.segments()) {
                LegacySnapshot.Key key = new LegacySnapshot.Key(segment.sourceNamespace(), segment.sourceName());
                if (!applied.contains(key) && result.segments().stream().noneMatch(s -> s.targetKey().equals(segment.targetKey()))) {
                    problems.add("segment [" + key + "] of the plan was not applied");
                }
            }
            Optional<NamespaceState> slots = slotTarget.namespace(plan.namespace());
            if (slots.isEmpty()) {
                problems.add("M2: the 5.0 namespace has no slot metadata");
            } else {
                NamespaceState state = slots.get();
                if (!TargetAdmin.matches(state, result.slots().limit(), result.slots().toleranceMillis(), result.slots().slotOffset())) {
                    problems.add("M2: the namespace is recorded with limit " + state.limit() + ", tolerance " + state.toleranceMillis() + "ms and slot offset "
                        + state.slotOffset());
                }
                if (state.slots() < state.limit()) {
                    problems.add("M2: only " + state.slots() + " of " + state.limit() + " slots exist");
                }
                if (state.minNextFloor() < result.w()) {
                    problems.add("M2: a slot's next floor is " + state.minNextFloor() + ", before W = " + result.w());
                }
            }
            boolean rolling = result.shared() != null && result.shared().rolling();
            if (rolling) {
                // 4.x runs on: its machine ids must stay below the slot offset, or 5.0 nodes collide with them (§2.7).
                int maxMachineId = snapshot.maxMachineId(plan.namespace());
                out.println("  4.x machine ids of [" + plan.namespace() + "] up to " + maxMachineId + ", slot offset " + result.slots().slotOffset());
                if (maxMachineId >= result.slots().slotOffset()) {
                    problems.add("M2: 4.x handed out machine id " + maxMachineId + " in the shared namespace, not below the slot offset "
                        + result.slots().slotOffset() + ": 4.x grew beyond the offset, and its nodes collide with 5.0's; stop it and move the services");
                }
            } else if (snapshot.maxTimestamp() > result.sourceMaxTimestamp()) {
                problems.add("M2: a 4.x machine record moved to " + snapshot.maxTimestamp() + " after the migration (T4 was "
                    + result.sourceMaxTimestamp() + "): a 4.x instance still runs");
            }
            if (result.generatorNamespace() != null) {
                Plan.GeneratorNamespace recorded = result.generatorNamespace();
                Optional<NamespaceState> shared = slotTarget.namespace(recorded.namespace());
                if (shared.isEmpty() || !TargetAdmin.matches(shared.get(), recorded.limit(), recorded.toleranceMillis(), recorded.slotOffset())) {
                    problems.add("the shared namespace [" + recorded.namespace() + "] is no longer recorded as " + recorded
                        + ": cosid.machines.generator would not match it");
                }
            }
        }
        if (!plan.layoutsOk()) {
            problems.add("M3: " + failed(plan.layouts()));
        }
        out.println("Verified namespace [" + plan.namespace() + "]: " + checked + " key(s), " + (problems.isEmpty() ? "M1-M3 hold" : problems.size() + " problem(s)"));
        problems.forEach(problem -> out.println("  FAIL " + problem));
        return problems;
    }

    /**
     * {@code purge} options.
     *
     * @param to             the 5.0 store
     * @param namespace      the namespace
     * @param before         delete sealed period keys of periods before this one ({@code 2025}, {@code 202501}, ...)
     * @param confirmStopped {@code --confirm-stopped}: 5.0 does not run
     * @param dryRun         only list
     */
    public record PurgeOptions(StoreLocation to, String namespace, String before, boolean confirmStopped, boolean dryRun) {
    }

    /**
     * Deletes sealed period keys {@code {namespace}/{name}/{period}} of periods before {@code before} (D23: offline only;
     * 09 §3). Keys that are not sealed are kept and listed.
     *
     * @param options the options
     * @return the deleted keys
     */
    public List<String> purge(PurgeOptions options) {
        Objects.requireNonNull(options.before(), "before");
        if (!options.before().matches("\\d{4}|\\d{6}|\\d{8}")) {
            throw new IllegalArgumentException("--before must be a period key: yyyy, yyyyMM or yyyyMMdd: " + options.before());
        }
        if (!options.dryRun() && !options.confirmStopped()) {
            throw new MigrationAbortedException("purge deletes keys: stop 5.0 and pass --confirm-stopped, or list them with --dry-run");
        }
        List<String> deleted = new ArrayList<>();
        try (TargetAdmin target = targets.apply(options.to())) {
            int live = target.namespace(options.namespace()).map(NamespaceState::live).orElse(0);
            if (live > 0 && !options.dryRun()) {
                throw new MigrationAbortedException("the 5.0 namespace has " + live + " live slot lease(s): purge runs offline only (D23), stop 5.0 first");
            }
            String prefix = options.namespace() + "/";
            for (RangeState range : target.ranges(prefix)) {
                String[] parts = range.key().substring(prefix.length()).split("/");
                if (parts.length != 2 || parts[1].length() != options.before().length() || !parts[1].matches("\\d+")
                    || parts[1].compareTo(options.before()) >= 0) {
                    continue;
                }
                if (!range.sealed()) {
                    out.println("  kept " + range.key() + ": not sealed");
                    continue;
                }
                if (options.dryRun()) {
                    out.println("  would delete " + range.key());
                    deleted.add(range.key());
                } else if (target.delete(range.key())) {
                    out.println("  deleted " + range.key());
                    deleted.add(range.key());
                }
            }
        }
        out.println((options.dryRun() ? "Would delete " : "Deleted ") + deleted.size() + " sealed period key(s) before " + options.before());
        return deleted;
    }

    /**
     * {@code config} options.
     *
     * @param input  the 4.x configuration
     * @param result {@code apply-result.json}, for {@code not-before}; optional
     * @param output the 5.0 configuration to write
     * @param zone   the zone of periodic segments where 4.x used the server default; optional
     * @param allowNew segment generators that may have no key in {@code apply-result.json} ({@code --allow-new}): 4.x
     *                 never issued from them
     */
    public record ConfigOptions(Path input, Path result, Path output, java.time.ZoneId zone, Set<String> allowNew) {
        /** Without generators allowed to be new. */
        public ConfigOptions(Path input, Path result, Path output, java.time.ZoneId zone) {
            this(input, result, output, zone, Set.of());
        }
    }

    /**
     * Converts a 4.x configuration (09 §3 {@code config}). Every segment and periodic segment generator it writes must
     * continue a key of {@code apply-result.json}, or be named with {@code --allow-new} (rc-review D-B2): a generator
     * whose name misses its migrated key would start over and issue the 4.x ids again.
     *
     * @param options the options
     * @return the conversion
     * @throws MigrationAbortedException if a segment generator has no migrated key, or the 4.x CosIdGenerator bits
     *                                   do not fit a 5.0 cosid80 ({@link Legacy4x#cosidLayoutProblem()})
     */
    public ConfigConverter.Conversion config(ConfigOptions options) {
        ApplyResult result = options.result() == null ? null : Json.read(options.result(), ApplyResult.class);
        FlatConfig input = FlatConfig.load(options.input());
        new Legacy4x(input).cosidLayoutProblem().ifPresent(reason -> {
            throw new MigrationAbortedException(reason);
        });
        ConfigConverter.Conversion conversion = new ConfigConverter(options.zone()).convert(input, result);
        List<String> missing = conversion.unmigratedSegments(result).stream().filter(name -> !options.allowNew().contains(name)).toList();
        if (!missing.isEmpty()) {
            throw new MigrationAbortedException("segment generators " + missing + " continue no key of "
                + (result == null ? "an apply result: pass --result apply-result.json" : options.result())
                + "; a generator that misses its migrated key starts over and issues the 4.x ids again. Name a generator 4.x never"
                + " issued from with --allow-new <generator>");
        }
        conversion.write(options.output());
        out.println("Wrote " + options.output() + (conversion.report().isEmpty() ? "" : "; not converted as is:"));
        conversion.report().forEach(line -> out.println("  " + line));
        return conversion;
    }
}
