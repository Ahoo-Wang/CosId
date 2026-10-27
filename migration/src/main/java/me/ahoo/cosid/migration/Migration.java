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
import me.ahoo.cosid.migration.source.SourceSnapshot;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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
     * @param namespace     the namespace
     * @param layoutConfig  the 4.x configuration, optional
     * @param targetConfig  the 5.0 configuration, optional: layouts are compared with it instead of the converted ones
     * @param slotBits      the slot bits, null for the 4.x machine bits (10 by default)
     * @param tolerance     the tolerance of the 5.0 namespace
     * @param sourceSkew    the source skew of the W estimate
     * @param renames       4.x to 5.0 generator names
     * @param groupPatterns 5.0 generator name to the 4.x group key pattern
     * @param output        where {@code plan.json} goes
     */
    public record PlanOptions(StoreLocation from, StoreLocation to, String namespace, Path layoutConfig, Path targetConfig, Integer slotBits,
                              Duration tolerance, Duration sourceSkew, Map<String, String> renames, Map<String, String> groupPatterns, Path output) {
    }

    /**
     * Makes a plan: read-only on both stores.
     *
     * @param options the options
     * @return the plan, also written to {@code options.output()}
     */
    public Plan plan(PlanOptions options) {
        List<String> warnings = new ArrayList<>();
        Legacy4x legacy = options.layoutConfig() == null ? null : new Legacy4x(FlatConfig.load(options.layoutConfig()));
        Map<String, String> renames = new TreeMap<>();
        Map<String, String> groupPatterns = new TreeMap<>();
        if (legacy != null) {
            renames.putAll(legacy.renames());
            groupPatterns.putAll(legacy.groupPatterns());
            warnings.addAll(legacy.ownNamespaces());
            if (!legacy.namespace().equals(options.namespace())) {
                warnings.add("--layout-config has namespace [" + legacy.namespace() + "], the plan [" + options.namespace() + "]");
            }
        }
        renames.putAll(options.renames());
        groupPatterns.putAll(options.groupPatterns());
        int bits = options.slotBits() != null ? options.slotBits() : legacy != null ? legacy.machineBit() : Legacy4x.DEFAULT_MACHINE_BIT;
        Plan.Slots slots = Plan.Slots.of(bits, options.tolerance().toMillis());

        SourceSnapshot snapshot;
        try (SourceReader source = sources.apply(options.from())) {
            snapshot = source.read(options.namespace());
        }
        KeyMapper mapper = new KeyMapper(options.namespace(), renames, groupPatterns);
        List<String> unrecognized = new ArrayList<>(snapshot.unrecognized());
        List<Plan.SegmentPlan> segments = new ArrayList<>();
        long targetNow;
        try (TargetAdmin target = targets.apply(options.to())) {
            targetNow = target.now();
            Map<String, String> owners = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : snapshot.sortedSegments().entrySet()) {
                KeyMapper.Mapping mapping = mapper.map(entry.getKey());
                if (!mapping.mapped()) {
                    unrecognized.add(options.namespace() + "." + entry.getKey() + ": " + mapping.problem());
                    continue;
                }
                String other = owners.putIfAbsent(mapping.targetKey(), entry.getKey());
                if (other != null) {
                    warnings.add("4.x segments [" + other + "] and [" + entry.getKey() + "] both map to [" + mapping.targetKey() + "]: the larger value wins");
                }
                Long targetNext = target.range(mapping.targetKey()).map(RangeState::next).orElse(null);
                segments.add(new Plan.SegmentPlan(entry.getKey(), mapping.generator(), mapping.period(), mapping.targetKey(), entry.getValue(),
                    next(entry.getKey(), entry.getValue(), 0), targetNext));
            }
            target.namespace(options.namespace()).ifPresent(state -> {
                if (state.limit() != slots.limit() || state.toleranceMillis() != slots.toleranceMillis()) {
                    warnings.add("the 5.0 namespace is recorded with limit " + state.limit() + " and tolerance " + state.toleranceMillis()
                        + "ms: apply fails unless --slot-bits / --tolerance match");
                }
                if (state.live() > 0) {
                    warnings.add("the 5.0 namespace has " + state.live() + " live slot lease(s): stop 5.0 before apply");
                }
            });
        }
        long sourceSkew = options.sourceSkew().toMillis();
        long w = floorW(targetNow, snapshot.maxMachineTimestamp(), sourceSkew);
        List<LayoutCheck> layouts = layouts(legacy, options.targetConfig(), w);
        if (legacy == null) {
            warnings.add("no --layout-config: layouts are not validated (M3), group patterns default to the 5.0 presets");
        }
        Plan plan = new Plan(Plan.FORMAT, Instant.now().toString(), options.namespace(), options.from().redacted(), options.to().redacted(), slots,
            renames, groupPatterns, segments, unrecognized,
            new Plan.Machines(snapshot.machines().size(), snapshot.maxMachineTimestamp(), targetNow, sourceSkew, w), layouts, warnings);
        Json.write(options.output(), plan);
        report(plan);
        return plan;
    }

    private void report(Plan plan) {
        out.println("Plan for namespace [" + plan.namespace() + "]: " + plan.source() + " -> " + plan.target());
        for (Plan.SegmentPlan segment : plan.segments()) {
            out.println("  segment " + plan.namespace() + "." + segment.sourceName() + " = " + segment.lastMaxId() + "  ->  " + segment.targetKey()
                + " next >= " + segment.next() + (segment.targetNext() == null ? "" : " (target has " + segment.targetNext() + ")"));
        }
        for (String key : plan.unrecognized()) {
            out.println("  NOT MIGRATED " + key);
        }
        Plan.Machines machines = plan.machines();
        out.println("  machine records: " + machines.records() + ", T4 = " + machines.maxTimestamp() + "; W estimate = " + machines.estimatedW()
            + " (" + Instant.ofEpochMilli(machines.estimatedW()) + ")");
        out.println("  slots: limit " + plan.slots().limit() + ", tolerance " + plan.slots().toleranceMillis() + "ms, next floor W");
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
     * @param plan           the plan file
     * @param confirmStopped {@code --confirm-stopped}: the operator stopped every 4.x instance
     * @param observe        the quiet period; zero turns the check off
     * @param gap            added to every segment's next value
     * @param sourceSkew     the source skew of W
     * @param sealSource     whether to seal the source (on unless {@code --no-seal-source})
     * @param sealGap        how far sealing advances a 4.x segment
     * @param result         where {@code apply-result.json} goes
     * @param fromOptions    options of the 4.x store not kept in the plan, such as the password
     * @param toOptions      options of the 5.0 store not kept in the plan
     * @param layoutConfig   the 4.x configuration, to check the layouts again with the real W; optional
     * @param targetConfig   the 5.0 configuration of that check; optional
     */
    public record ApplyOptions(Path plan, boolean confirmStopped, Duration observe, long gap, Duration sourceSkew, boolean sealSource, long sealGap,
                               Path result, Map<String, String> fromOptions, Map<String, String> toOptions, Path layoutConfig, Path targetConfig) {
    }

    /**
     * Applies a plan (09 §2, §3): idempotent, values only ever increase.
     *
     * <ol>
     *     <li>Snapshots the source, waits the quiet period, snapshots again; any change aborts.</li>
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
        if (!options.confirmStopped()) {
            throw new MigrationAbortedException("apply needs --confirm-stopped: stop every 4.x instance of the namespace first (09 §2)");
        }
        if (options.gap() < 0 || options.sealGap() <= 0) {
            throw new IllegalArgumentException("--gap must not be negative and --seal-gap must be positive");
        }
        Plan plan = Json.read(options.plan(), Plan.class);
        if (!Plan.FORMAT.equals(plan.format())) {
            throw new MigrationAbortedException(options.plan() + " is no " + Plan.FORMAT + " file");
        }
        if (!plan.layoutsOk()) {
            throw new MigrationAbortedException("the plan failed the layout validation (M3): " + failed(plan.layouts()));
        }
        StoreLocation from = plan.source().withOptions(options.fromOptions()).withEnvironmentPassword("FROM");
        StoreLocation to = plan.target().withOptions(options.toOptions()).withEnvironmentPassword("TO");
        List<String> warnings = new ArrayList<>();
        Map<String, Plan.SegmentPlan> planned = new LinkedHashMap<>();
        plan.segments().forEach(segment -> planned.put(segment.sourceName(), segment));
        KeyMapper mapper = new KeyMapper(plan.namespace(), plan.renames(), plan.groupPatterns());
        try (SourceReader source = sources.apply(from); TargetAdmin target = targets.apply(to)) {
            SourceSnapshot first = source.read(plan.namespace());
            long observed = 0;
            SourceSnapshot snapshot = first;
            if (!options.observe().isZero()) {
                out.println("Watching the 4.x store for " + options.observe() + " (--observe) ...");
                long started = System.nanoTime();
                try {
                    sleeper.sleep(options.observe());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new MigrationAbortedException("interrupted during the quiet period");
                }
                snapshot = source.read(plan.namespace());
                observed = Duration.ofNanos(System.nanoTime() - started).toMillis();
                List<String> changes = snapshot.changesSince(first);
                if (!changes.isEmpty()) {
                    throw new MigrationAbortedException("the 4.x store changed during the quiet period, a 4.x instance still runs:\n  " + String.join("\n  ", changes));
                }
            } else {
                warnings.add("the quiet-period check was off (--observe 0)");
            }
            for (String name : snapshot.segments().keySet()) {
                if (!planned.containsKey(name)) {
                    warnings.add("segment [" + name + "] is new since the plan: migrated as well");
                }
            }
            for (String name : planned.keySet()) {
                if (!snapshot.segments().containsKey(name)) {
                    throw new MigrationAbortedException("segment [" + name + "] of the plan is gone from the 4.x store");
                }
            }

            long appliedAt = target.now();
            long sourceSkew = options.sourceSkew().toMillis();
            long w = floorW(appliedAt, snapshot.maxMachineTimestamp(), sourceSkew);
            if (options.layoutConfig() != null) {
                List<LayoutCheck> checks = layouts(new Legacy4x(FlatConfig.load(options.layoutConfig())), options.targetConfig(), w);
                if (!checks.stream().allMatch(LayoutCheck::ok)) {
                    throw new MigrationAbortedException("the layout validation (M3) fails at W = " + w + ": " + failed(checks));
                }
            }

            // 1. Seal: one atomic increment per segment; the result bounds every range 4.x reserved before it.
            Map<String, Long> lastMaxIds = new TreeMap<>();
            Map<String, Long> sealedAt = new TreeMap<>();
            for (Map.Entry<String, Long> entry : snapshot.sortedSegments().entrySet()) {
                String name = entry.getKey();
                long current = entry.getValue();
                if (!options.sealSource()) {
                    lastMaxIds.put(name, current);
                    continue;
                }
                Plan.SegmentPlan segment = planned.get(name);
                if (segment != null && current - segment.lastMaxId() >= options.sealGap()) {
                    // Sealed by an earlier run of this plan: what 4.x reserved before that seal is at most current - sealGap.
                    lastMaxIds.put(name, current - options.sealGap());
                    sealedAt.put(name, current);
                    warnings.add("segment [" + name + "] was already sealed at " + current + " by an earlier apply");
                    continue;
                }
                long sealed = source.seal(plan.namespace(), name, options.sealGap());
                lastMaxIds.put(name, sealed - options.sealGap());
                sealedAt.put(name, sealed);
            }

            // 2. Ranges (M1): next = lastMaxId + 1 + gap, only ever raised.
            Map<String, ApplyResult.SegmentResult> results = new LinkedHashMap<>();
            Map<String, Long> wanted = new LinkedHashMap<>();
            Map<String, String> sourceOf = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : lastMaxIds.entrySet()) {
                KeyMapper.Mapping mapping = mapper.map(entry.getKey());
                if (!mapping.mapped()) {
                    warnings.add("segment [" + entry.getKey() + "] is not migrated: " + mapping.problem());
                    continue;
                }
                long next = next(entry.getKey(), entry.getValue(), options.gap());
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
                String name = sourceOf.get(entry.getKey());
                results.put(entry.getKey(), new ApplyResult.SegmentResult(name, entry.getKey(), lastMaxIds.get(name), entry.getValue(), state.next(),
                    sealedAt.get(name)));
            }

            // 3. Slots (M2): metadata, every slot free, next floor W.
            NamespaceState slots = target.prepare(plan.namespace(), plan.slots().limit(), plan.slots().toleranceMillis(), w);
            ApplyResult result = new ApplyResult(ApplyResult.FORMAT, Json.sha256(options.plan()), plan.namespace(), plan.source(), plan.target(), appliedAt,
                snapshot.maxMachineTimestamp(), sourceSkew, w, Instant.ofEpochMilli(w).toString(), options.gap(), options.sealSource(), options.sealGap(),
                observed, plan.slots(), List.copyOf(results.values()), warnings);
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

    private static String failed(List<LayoutCheck> checks) {
        return checks.stream().filter(check -> !check.ok()).map(check -> check.generator() + " (" + check.reason() + ")").toList().toString();
    }

    /**
     * {@code verify} options.
     *
     * @param plan        the plan file
     * @param result      the result file
     * @param fromOptions options of the 4.x store not kept in the plan
     * @param toOptions   options of the 5.0 store not kept in the plan
     */
    public record VerifyOptions(Path plan, Path result, Map<String, String> fromOptions, Map<String, String> toOptions) {
    }

    /**
     * Reads both stores again and checks M1–M3 and that the source did not grow after the seal (09 §3).
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
        int checked = 0;
        try (SourceReader source = sources.apply(from); TargetAdmin target = targets.apply(to)) {
            SourceSnapshot snapshot = source.read(plan.namespace());
            for (ApplyResult.SegmentResult segment : result.segments()) {
                checked++;
                Optional<RangeState> range = target.range(segment.targetKey());
                if (range.isEmpty()) {
                    problems.add("M1: 5.0 key [" + segment.targetKey() + "] is missing");
                } else if (range.get().next() <= segment.lastMaxId()) {
                    problems.add("M1: 5.0 key [" + segment.targetKey() + "] is at " + range.get().next() + ", not past the 4.x value " + segment.lastMaxId());
                }
                Long now = snapshot.segments().get(segment.sourceName());
                long expected = segment.sealedAt() != null ? segment.sealedAt() : segment.lastMaxId();
                if (now == null) {
                    problems.add("4.x segment [" + segment.sourceName() + "] is gone");
                } else if (now > expected) {
                    problems.add("4.x segment [" + segment.sourceName() + "] grew to " + now + " after " + (segment.sealedAt() != null ? "the seal at " : "the migration at ")
                        + expected + ": a 4.x instance still runs" + (segment.sealedAt() != null ? " (its ids stay far from 5.0's while 5.0 is below " + segment.sealedAt() + ")" : ""));
                }
            }
            for (Plan.SegmentPlan segment : plan.segments()) {
                if (result.segments().stream().noneMatch(applied -> applied.sourceName().equals(segment.sourceName()))) {
                    problems.add("segment [" + segment.sourceName() + "] of the plan was not applied");
                }
            }
            Optional<NamespaceState> slots = target.namespace(plan.namespace());
            if (slots.isEmpty()) {
                problems.add("M2: the 5.0 namespace has no slot metadata");
            } else {
                NamespaceState state = slots.get();
                if (state.limit() != result.slots().limit() || state.toleranceMillis() != result.slots().toleranceMillis()) {
                    problems.add("M2: the namespace is recorded with limit " + state.limit() + " and tolerance " + state.toleranceMillis() + "ms");
                }
                if (state.slots() < state.limit()) {
                    problems.add("M2: only " + state.slots() + " of " + state.limit() + " slots exist");
                }
                if (state.minNextFloor() < result.w()) {
                    problems.add("M2: a slot's next floor is " + state.minNextFloor() + ", before W = " + result.w());
                }
            }
            if (snapshot.maxMachineTimestamp() > result.sourceMaxTimestamp()) {
                problems.add("M2: a 4.x machine record moved to " + snapshot.maxMachineTimestamp() + " after the migration (T4 was "
                    + result.sourceMaxTimestamp() + "): a 4.x instance still runs");
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
     */
    public record ConfigOptions(Path input, Path result, Path output, java.time.ZoneId zone) {
    }

    /**
     * Converts a 4.x configuration (09 §3 {@code config}).
     *
     * @param options the options
     * @return the conversion
     */
    public ConfigConverter.Conversion config(ConfigOptions options) {
        ApplyResult result = options.result() == null ? null : Json.read(options.result(), ApplyResult.class);
        ConfigConverter.Conversion conversion = new ConfigConverter(options.zone()).convert(FlatConfig.load(options.input()), result);
        conversion.write(options.output());
        out.println("Wrote " + options.output() + (conversion.report().isEmpty() ? "" : "; not converted as is:"));
        conversion.report().forEach(line -> out.println("  " + line));
        return conversion;
    }
}
