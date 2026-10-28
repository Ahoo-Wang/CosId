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

package me.ahoo.cosid.migration.config;

import me.ahoo.cosid.time.Layout;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the migration needs from a 4.x {@code cosid.*} configuration ({@code --layout-config}, {@code config --input}):
 * the generators and their 5.0 names, the time-based layouts (M3), the machine bits and the group patterns of grouped
 * segments, with the 4.x defaults of {@code cosid-spring-boot-starter} 4.1.
 */
public final class Legacy4x {
    /** The 4.x name of the shared generator ({@code IdGeneratorProvider.SHARE}). */
    public static final String SHARE = "__share__";
    /** The 5.0 name of the shared generator. */
    public static final String DEFAULT = "default";
    /** The 5.0 name of the segment share when the snowflake share is also enabled and keeps {@link #DEFAULT}. */
    public static final String SEGMENT_SHARE = "segment-share";
    public static final long COSID_EPOCH = 1577203200000L;
    public static final int DEFAULT_MACHINE_BIT = 10;
    private final FlatConfig config;

    public Legacy4x(FlatConfig config) {
        this.config = config;
    }

    public FlatConfig config() {
        return config;
    }

    public String namespace() {
        return config.get("cosid.namespace", "cosid");
    }

    public boolean snowflakeEnabled() {
        return config.bool("cosid.snowflake.enabled", false);
    }

    public boolean segmentEnabled() {
        return config.bool("cosid.segment.enabled", false);
    }

    public boolean cosidEnabled() {
        return config.bool("cosid.generator.enabled", false);
    }

    public boolean machineEnabled() {
        return config.bool("cosid.machine.enabled", false);
    }

    public int machineBit() {
        return (int) config.number("cosid.machine.machine-bit", DEFAULT_MACHINE_BIT);
    }

    boolean snowflakeShare() {
        return snowflakeEnabled() && config.bool("cosid.snowflake.share.enabled", true);
    }

    boolean segmentShare() {
        return segmentEnabled() && config.bool("cosid.segment.share.enabled", true);
    }

    /**
     * The 5.0 name of the segment share.
     *
     * @return {@code default}, or {@code segment-share} when the snowflake share takes {@code default}
     */
    public String segmentShareName() {
        return snowflakeShare() ? SEGMENT_SHARE : DEFAULT;
    }

    /**
     * The renames of 4.x segment names the key mapping needs: {@code __share__} to the segment share's 5.0 name.
     *
     * @return the renames
     */
    public Map<String, String> renames() {
        return Map.of(SHARE, segmentShareName());
    }

    /**
     * The snowflake generators: 5.0 name to the canonical key prefix of their 4.x definition.
     *
     * @return the generators, share first
     */
    public Map<String, String> snowflakes() {
        Map<String, String> generators = new LinkedHashMap<>();
        if (snowflakeShare()) {
            generators.put(DEFAULT, "cosid.snowflake.share");
        }
        if (snowflakeEnabled()) {
            for (String name : config.names("cosid.snowflake.provider")) {
                generators.put(name, "cosid.snowflake.provider." + name);
            }
        }
        return generators;
    }

    /**
     * The segment generators: 5.0 name to the canonical key prefix of their 4.x definition.
     *
     * @return the generators, share first
     */
    public Map<String, String> segments() {
        Map<String, String> generators = new LinkedHashMap<>();
        if (segmentShare()) {
            generators.put(segmentShareName(), "cosid.segment.share");
        }
        if (segmentEnabled()) {
            for (String name : config.names("cosid.segment.provider")) {
                generators.put(name, "cosid.segment.provider." + name);
            }
        }
        return generators;
    }

    /**
     * Whether a snowflake definition counts seconds.
     *
     * @param prefix the key prefix of the definition
     * @return true for {@code timestamp-unit: second}
     */
    public boolean seconds(String prefix) {
        return "second".equals(config.get(prefix + ".timestamp-unit", "millisecond").toLowerCase(Locale.ROOT));
    }

    /**
     * The layout of a 4.x snowflake definition.
     *
     * @param prefix the key prefix of the definition
     * @return the layout
     */
    public Layout snowflakeLayout(String prefix) {
        long epoch = config.number(prefix + ".epoch", 0);
        if (epoch <= 0) {
            epoch = config.number("cosid.snowflake.epoch", COSID_EPOCH);
        }
        // 4.x reads any epoch other than the default in the id's unit: seconds for timestamp-unit second.
        if (seconds(prefix) && epoch != COSID_EPOCH) {
            epoch = Math.multiplyExact(epoch, 1000L);
        }
        int machineBit = (int) config.number(prefix + ".machine-bit", machineBit());
        int sequenceBit = (int) config.number(prefix + ".sequence-bit", 12);
        long threshold = config.number(prefix + ".sequence-reset-threshold", Layout.defaultSeqResetThreshold(sequenceBit));
        return new Layout(Instant.ofEpochMilli(epoch), seconds(prefix) ? ChronoUnit.SECONDS : ChronoUnit.MILLIS,
            (int) config.number(prefix + ".timestamp-bit", 41), machineBit, sequenceBit, threshold);
    }

    /**
     * The layout of the 4.x {@code CosIdGenerator} (80 bits, Unix epoch).
     *
     * @return the layout
     */
    public Layout cosidLayout() {
        int sequenceBit = (int) config.number("cosid.generator.sequence-bit", 16);
        return new Layout(Instant.EPOCH, ChronoUnit.MILLIS, (int) config.number("cosid.generator.timestamp-bit", 44),
            (int) config.number("cosid.generator.machine-bit", 20), sequenceBit,
            config.number("cosid.generator.sequence-reset-threshold", Layout.defaultSeqResetThreshold(sequenceBit)));
    }

    /**
     * Every time-based layout: 5.0 generator name to the 4.x layout.
     *
     * @return the layouts
     */
    public Map<String, Layout> layouts() {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        snowflakes().forEach((name, prefix) -> layouts.put(name, snowflakeLayout(prefix)));
        if (cosidEnabled()) {
            layouts.put("cosid", cosidLayout());
        }
        return layouts;
    }

    /**
     * The period preset of a grouped segment definition.
     *
     * @param prefix the key prefix of the definition
     * @return {@code YEAR}, {@code YEAR_MONTH} or {@code YEAR_MONTH_DAY}; null when not grouped
     */
    public String period(String prefix) {
        String by = config.get(prefix + ".group.by", "never").toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (by) {
            case "YEAR", "YEAR_MONTH", "YEAR_MONTH_DAY" -> by;
            default -> null;
        };
    }

    /**
     * The group patterns of the grouped segments: 5.0 generator name to the 4.x pattern.
     *
     * @return the patterns
     */
    public Map<String, String> groupPatterns() {
        Map<String, String> patterns = new LinkedHashMap<>();
        segments().forEach((name, prefix) -> {
            String pattern = config.get(prefix + ".group.pattern");
            if (period(prefix) != null && pattern != null) {
                patterns.put(name, pattern);
            }
        });
        return patterns;
    }

    /**
     * The 4.x namespaces other than {@link #namespace()} that definitions use ({@code provider.<name>.namespace}): their
     * segment keys are {@code {ownNs}.{name}}, and a snowflake's machine id is distributed in its own namespace too.
     *
     * @return namespace to the 4.x segment names kept there (empty for a namespace of snowflakes only)
     */
    public Map<String, Set<String>> extraNamespaces() {
        Map<String, Set<String>> extra = new TreeMap<>();
        snowflakes().forEach((name, prefix) -> ownNamespace(prefix).ifPresent(namespace -> extra.computeIfAbsent(namespace, key -> new TreeSet<>())));
        segments().forEach((name, prefix) -> ownNamespace(prefix).ifPresent(namespace -> extra.computeIfAbsent(namespace, key -> new TreeSet<>())
            .add(prefix.equals("cosid.segment.share") ? SHARE : name)));
        return extra;
    }

    private Optional<String> ownNamespace(String prefix) {
        String namespace = config.get(prefix + ".namespace");
        return namespace == null || namespace.isBlank() || namespace.equals(namespace()) ? Optional.empty() : Optional.of(namespace);
    }

    /**
     * The store type of the 4.x segments ({@code cosid.segment.distributor.type}, {@code redis} by default).
     *
     * @return the type, empty without segments
     */
    public Optional<String> segmentStoreType() {
        return segments().isEmpty() ? Optional.empty() : Optional.of(config.get("cosid.segment.distributor.type", "redis").toLowerCase(Locale.ROOT));
    }

    /**
     * The store type of the 4.x machine ids ({@code cosid.machine.distributor.type}), when it is a store.
     *
     * @return {@code jdbc}, {@code redis}, {@code mongo} or {@code zookeeper}; empty for manual, stateful_set, proxy or
     *     no machine
     */
    public Optional<String> machineStoreType() {
        if (!machineEnabled()) {
            return Optional.empty();
        }
        String type = config.get("cosid.machine.distributor.type", "manual").toLowerCase(Locale.ROOT);
        return Set.of("jdbc", "redis", "mongo", "zookeeper").contains(type) ? Optional.of(type) : Optional.empty();
    }

    /**
     * The 5.0 layout preset a layout is, if any.
     *
     * @param layout the layout
     * @return {@code SNOWFLAKE_MS}, {@code SNOWFLAKE_S}, {@code JS_SAFE_MS}, {@code JS_SAFE_S} or null
     */
    public static String preset(Layout layout) {
        Map<String, Layout> presets = new LinkedHashMap<>();
        presets.put("SNOWFLAKE_MS", Layout.SNOWFLAKE_MS);
        presets.put("SNOWFLAKE_S", Layout.SNOWFLAKE_S);
        presets.put("JS_SAFE_MS", Layout.JS_SAFE_MS);
        presets.put("JS_SAFE_S", Layout.JS_SAFE_S);
        for (Map.Entry<String, Layout> preset : presets.entrySet()) {
            if (preset.getValue().equals(layout)) {
                return preset.getKey();
            }
        }
        return null;
    }
}
