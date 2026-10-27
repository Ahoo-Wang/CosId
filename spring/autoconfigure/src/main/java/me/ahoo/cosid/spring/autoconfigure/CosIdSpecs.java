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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.PartsCodecSpec;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * {@link CosIdProperties} to {@link RuntimeSpec} (07-runtime-and-spring.md §4.1): a pure function, no Spring involved.
 * It checks what the properties alone can tell (a missing strategy, a codec item naming two codecs, ...) and reports
 * every problem at once by property path; {@code CosIdRuntime.start} validates the spec as a whole.
 */
public final class CosIdSpecs {
    public static final String SNOWFLAKE = "snowflake";
    public static final String COSID80 = "cosid80";
    public static final String SEGMENT = "segment";
    public static final String PERIODIC_SEGMENT = "periodic-segment";

    private static final Map<String, Layout> LAYOUTS = Map.of(
        "SNOWFLAKE_MS", Layout.SNOWFLAKE_MS,
        "SNOWFLAKE_S", Layout.SNOWFLAKE_S,
        "JS_SAFE_MS", Layout.JS_SAFE_MS,
        "JS_SAFE_S", Layout.JS_SAFE_S);
    private static final Map<String, Period> PERIODS = Map.of(
        "YEAR", Period.YEAR,
        "YEAR_MONTH", Period.YEAR_MONTH,
        "YEAR_MONTH_DAY", Period.YEAR_MONTH_DAY);

    private CosIdSpecs() {
    }

    /**
     * Maps the properties to a spec.
     *
     * @param properties the bound properties
     * @param zone       the zone of the zone-dependent settings left empty (the system zone in an application)
     * @return the spec
     * @throws CosIdException listing every problem by property path
     */
    public static RuntimeSpec toSpec(CosIdProperties properties, ZoneId zone) {
        List<String> problems = new ArrayList<>();
        Map<String, StoreSpec> stores = new LinkedHashMap<>();
        new TreeMap<>(properties.stores()).forEach((name, store) -> {
            String at = CosIdProperties.PREFIX + ".stores." + name;
            if (store.type() == null || store.type().isBlank()) {
                problems.add(at + ".type: required");
                return;
            }
            stores.put(name, new StoreSpec(store.type().trim(), store.timeout(), Map.copyOf(store.options())));
        });
        final Optional<MachineSpec> machine = machine(properties.machine(), problems);
        Map<String, GeneratorSpec> generators = new LinkedHashMap<>();
        new TreeMap<>(properties.generators()).forEach((name, generator) -> {
            GeneratorSpec spec = generator(CosIdProperties.PREFIX + ".generators." + name, generator, zone, problems);
            if (spec != null) {
                generators.put(name, spec);
            }
        });
        if (properties.namespace() == null || properties.namespace().isBlank()) {
            problems.add(CosIdProperties.PREFIX + ".namespace: must not be blank");
        }
        if (!problems.isEmpty()) {
            throw new CosIdException("Invalid " + CosIdProperties.PREFIX + ".* configuration:\n  - " + String.join("\n  - ", problems));
        }
        return new RuntimeSpec(properties.namespace(), stores, machine, generators);
    }

    private static Optional<MachineSpec> machine(CosIdProperties.Machine machine, List<String> problems) {
        String at = CosIdProperties.PREFIX + ".machine";
        if (machine == null || machine.leased() == null && machine.fixed() == null) {
            return Optional.empty();
        }
        if (machine.leased() != null && machine.fixed() != null) {
            problems.add(at + ": set either leased or static, not both");
            return Optional.empty();
        }
        if (machine.leased() != null) {
            CosIdProperties.Leased leased = machine.leased();
            if (leased.store() == null || leased.store().isBlank()) {
                problems.add(at + ".leased.store: required");
                return Optional.empty();
            }
            return Optional.of(new MachineSpec.Leased(leased.store(), leased.bits(), leased.ttl(), leased.tolerance(),
                leased.preferredSlot() == null ? OptionalInt.empty() : OptionalInt.of(leased.preferredSlot()),
                Optional.ofNullable(leased.ownerKey()).filter(key -> !key.isEmpty())));
        }
        CosIdProperties.Static fixed = machine.fixed();
        if (fixed.slot() == null) {
            problems.add(at + ".static.slot: required");
            return Optional.empty();
        }
        return Optional.of(new MachineSpec.Static(fixed.bits(), fixed.slot(), Optional.ofNullable(fixed.stateFile()), fixed.ahead(),
            Optional.ofNullable(fixed.notBefore())));
    }

    private static GeneratorSpec generator(String at, CosIdProperties.Generator generator, ZoneId zone, List<String> problems) {
        String strategy = generator.strategy() == null ? "" : generator.strategy().trim().toLowerCase(Locale.ROOT);
        if (!List.of(SNOWFLAKE, COSID80, SEGMENT, PERIODIC_SEGMENT).contains(strategy)) {
            problems.add(at + ".strategy: one of snowflake, cosid80, segment, periodic-segment, not [" + generator.strategy() + "]");
            return null;
        }
        // A disabled generator is not validated (D24): only its strategy, which gives its bean type, must be known.
        List<String> own = generator.disabled() ? new ArrayList<>() : problems;
        GeneratorSpec spec;
        try {
            spec = switch (strategy) {
                case SNOWFLAKE -> new GeneratorSpec.Snowflake(layout(at, generator, own), regression(generator), codecs(at, generator.codec(), zone, own));
                case COSID80 -> new GeneratorSpec.CosId80(regression(generator), partsCodec(at, generator.codec(), zone, own));
                case SEGMENT -> new GeneratorSpec.Segment(store(at, generator, own), batch(generator), codecs(at, generator.codec(), zone, own));
                default -> new GeneratorSpec.PeriodicSegment(store(at, generator, own), period(at, generator, own),
                    generator.zone() == null ? zone : generator.zone(), batch(generator),
                    new PeriodicPolicy(generator.prewarm(), Optional.ofNullable(generator.sealAfter())), groupedCodec(at, generator.codec(), zone, own));
            };
        } catch (IllegalArgumentException e) {
            own.add(at + ": " + e.getMessage());
            spec = null;
        }
        if (!generator.disabled()) {
            return spec;
        }
        return new GeneratorSpec.Disabled(spec != null && own.isEmpty() ? spec : placeholder(strategy, generator));
    }

    /** What a disabled generator that would not map stands for: only its strategy matters. */
    private static GeneratorSpec placeholder(String strategy, CosIdProperties.Generator generator) {
        String store = generator.store() == null ? "" : generator.store();
        return switch (strategy) {
            case SNOWFLAKE -> new GeneratorSpec.Snowflake(Layout.SNOWFLAKE_MS, ClockRegressionPolicy.DEFAULT, List.of());
            case COSID80 -> new GeneratorSpec.CosId80(ClockRegressionPolicy.DEFAULT, new PartsCodecSpec.Radix(62));
            case SEGMENT -> new GeneratorSpec.Segment(store, BatchPolicy.DEFAULT, List.of());
            default -> new GeneratorSpec.PeriodicSegment(store, Period.YEAR_MONTH_DAY, ZoneId.of("UTC"), BatchPolicy.DEFAULT,
                PeriodicPolicy.DEFAULT, new GroupedCodecSpec.DatePrefix("-", List.of()));
        };
    }

    private static Layout layout(String at, CosIdProperties.Generator generator, List<String> problems) {
        Layout preset = LAYOUTS.get(generator.layout() == null ? "" : generator.layout().trim().toUpperCase(Locale.ROOT));
        if (preset == null) {
            problems.add(at + ".layout: one of " + new TreeMap<>(LAYOUTS).keySet() + ", not [" + generator.layout() + "]");
            return Layout.SNOWFLAKE_MS;
        }
        if (generator.epoch() == null && generator.unit() == null && generator.timeBits() == null && generator.nodeBits() == null
            && generator.seqBits() == null && generator.seqResetThreshold() == null) {
            return preset;
        }
        int seqBits = generator.seqBits() == null ? preset.seqBits() : generator.seqBits();
        long seqResetThreshold;
        if (generator.seqResetThreshold() != null) {
            seqResetThreshold = generator.seqResetThreshold();
        } else {
            seqResetThreshold = generator.seqBits() == null ? preset.seqResetThreshold() : Layout.defaultSeqResetThreshold(seqBits);
        }
        return new Layout(generator.epoch() == null ? preset.epoch() : generator.epoch(),
            generator.unit() == null ? preset.unit() : generator.unit(),
            generator.timeBits() == null ? preset.timeBits() : generator.timeBits(),
            generator.nodeBits() == null ? preset.nodeBits() : generator.nodeBits(),
            seqBits,
            seqResetThreshold);
    }

    private static ClockRegressionPolicy regression(CosIdProperties.Generator generator) {
        CosIdProperties.Regression regression = generator.regression();
        return regression == null ? ClockRegressionPolicy.DEFAULT : new ClockRegressionPolicy(regression.borrowUpTo(), regression.waitUpTo());
    }

    private static String store(String at, CosIdProperties.Generator generator, List<String> problems) {
        if (generator.store() == null || generator.store().isBlank()) {
            problems.add(at + ".store: required");
            return "";
        }
        return generator.store();
    }

    private static BatchPolicy batch(CosIdProperties.Generator generator) {
        long maxBatch = generator.maxBatch() == null ? BatchPolicy.defaultMaxBatch(generator.step()) : generator.maxBatch();
        return new BatchPolicy(generator.step(), generator.initialOffset(), generator.lookahead(), maxBatch, generator.reserveTimeout(),
            Optional.ofNullable(generator.rangeTtl()));
    }

    private static Period period(String at, CosIdProperties.Generator generator, List<String> problems) {
        Period period = PERIODS.get(generator.period() == null ? "" : generator.period().trim().toUpperCase(Locale.ROOT));
        if (period == null) {
            problems.add(at + ".period: one of " + new TreeMap<>(PERIODS).keySet() + ", not [" + generator.period() + "]");
            return Period.YEAR_MONTH_DAY;
        }
        return period;
    }

    private static List<CodecSpec> codecs(String at, List<CosIdProperties.Codec> items, ZoneId zone, List<String> problems) {
        return codecs(at, items, 0, items.size(), zone, problems);
    }

    private static List<CodecSpec> codecs(String at, List<CosIdProperties.Codec> items, int from, int to, ZoneId zone, List<String> problems) {
        List<CodecSpec> codecs = new ArrayList<>();
        for (int i = from; i < to; i++) {
            CodecSpec codec = codec(at + ".codec[" + i + "]", items.get(i), zone, problems);
            if (codec != null) {
                codecs.add(codec);
            }
        }
        return codecs;
    }

    private static CodecSpec codec(String at, CosIdProperties.Codec item, ZoneId zone, List<String> problems) {
        if (!single(at, item, problems)) {
            return null;
        }
        if (item.period() != null) {
            problems.add(at + ".period: only a periodic segment has a period item, and only one");
            return null;
        }
        if (item.radix() != null) {
            CosIdProperties.Radix radix = item.radix();
            if (radix.radix() < Radix.MIN_RADIX || radix.radix() > Radix.MAX_RADIX) {
                problems.add(at + ".radix.radix: must be in [" + Radix.MIN_RADIX + ", " + Radix.MAX_RADIX + "]: " + radix.radix());
                return null;
            }
            return new CodecSpec.Radix(radix.radix(), radix.width() == null ? Radix.maxWidth(radix.radix()) : radix.width(), radix.padStart());
        }
        if (item.friendly() != null) {
            return new CodecSpec.Friendly(item.friendly().zone() == null ? zone : item.friendly().zone(), item.friendly().padStart());
        }
        if (item.prefix() != null) {
            return new CodecSpec.Prefix(item.prefix());
        }
        if (item.suffix() != null) {
            return new CodecSpec.Suffix(item.suffix());
        }
        if (item.datePrefix() != null) {
            CosIdProperties.DatePrefix date = item.datePrefix();
            return new CodecSpec.DatePrefix(date.pattern(), date.delimiter(), date.source(), date.zone() == null ? zone : date.zone());
        }
        return new CodecSpec.Custom(item.custom());
    }

    private static boolean single(String at, CosIdProperties.Codec item, List<String> problems) {
        int set = 0;
        for (Object field : new Object[] {item.radix(), item.friendly(), item.prefix(), item.suffix(), item.datePrefix(), item.custom(), item.period()}) {
            if (field != null) {
                set++;
            }
        }
        if (set != 1) {
            problems.add(at + ": set exactly one of radix, friendly, prefix, suffix, date-prefix, custom, period (" + set + " set)");
            return false;
        }
        return true;
    }

    private static PartsCodecSpec partsCodec(String at, List<CosIdProperties.Codec> items, ZoneId zone, List<String> problems) {
        if (items.isEmpty()) {
            return new PartsCodecSpec.Radix(62);
        }
        String first = at + ".codec[0]";
        if (items.size() > 1 || !single(first, items.get(0), problems)
            || items.get(0).radix() == null && items.get(0).friendly() == null) {
            problems.add(at + ".codec: a cosid80 generator takes a single radix or friendly item");
            return new PartsCodecSpec.Radix(62);
        }
        CosIdProperties.Codec item = items.get(0);
        if (item.radix() != null) {
            return new PartsCodecSpec.Radix(item.radix().radix());
        }
        return new PartsCodecSpec.Friendly(item.friendly().zone() == null ? zone : item.friendly().zone());
    }

    /**
     * The codec of a periodic segment: a {@code period} item puts the period key where it stands (4.x
     * {@code group-prefix}, before or after the business prefix) or, disabled, leaves it out; without one the list
     * starts with {@code date-prefix}, the period first.
     */
    private static GroupedCodecSpec groupedCodec(String at, List<CosIdProperties.Codec> items, ZoneId zone, List<String> problems) {
        GroupedCodecSpec fallback = new GroupedCodecSpec.DatePrefix("-", List.of());
        if (items.isEmpty()) {
            return fallback;
        }
        int periodAt = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).period() != null && periodAt < 0) {
                periodAt = i;
            }
        }
        if (periodAt >= 0) {
            if (!single(at + ".codec[" + periodAt + "]", items.get(periodAt), problems)) {
                return fallback;
            }
            CosIdProperties.PeriodText period = items.get(periodAt).period();
            List<CodecSpec> value = codecs(at, items, 0, periodAt, zone, problems);
            List<CodecSpec> outer = codecs(at, items, periodAt + 1, items.size(), zone, problems);
            if (!period.enabled()) {
                List<CodecSpec> all = new ArrayList<>(value);
                all.addAll(outer);
                return new GroupedCodecSpec.ValueOnly(all);
            }
            return new GroupedCodecSpec.DatePrefix(period.delimiter() == null ? "" : period.delimiter(), value, outer);
        }
        String first = at + ".codec[0]";
        if (!single(first, items.get(0), problems)) {
            return fallback;
        }
        if (items.get(0).datePrefix() == null) {
            problems.add(first + ": the codec list of a periodic segment has a period item or starts with date-prefix");
            return fallback;
        }
        List<CodecSpec> value = codecs(at, items, 1, items.size(), zone, problems);
        return new GroupedCodecSpec.DatePrefix(items.get(0).datePrefix().delimiter(), value);
    }
}
