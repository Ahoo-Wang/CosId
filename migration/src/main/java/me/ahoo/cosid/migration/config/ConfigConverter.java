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

import me.ahoo.cosid.migration.ApplyResult;
import me.ahoo.cosid.time.Layout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts a 4.x {@code cosid.*} configuration to the 5.0 model of 07-runtime-and-spring.md §4.1 (09 §3 {@code config}),
 * as far as it goes; whatever has no 5.0 counterpart, or changes behaviour, goes to the report, one line per key. The
 * StaticSlot {@code not-before} is W from {@code apply-result.json} (09 §4.4).
 */
public final class ConfigConverter {
    private static final YAMLMapper YAML = new YAMLMapper(new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS));
    private static final Set<String> STORE_TYPES = Set.of("jdbc", "redis", "mongo", "zookeeper");
    private final ZoneId zone;

    /**
     * A converter.
     *
     * @param zone the zone of periodic segments and friendly codecs where 4.x used the server default; null for the
     *             default zone of this JVM
     */
    public ConfigConverter(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * The converted configuration and the report.
     *
     * @param model  the 5.0 configuration as nested maps, {@code cosid} at the root
     * @param report what was not converted as is
     */
    public record Conversion(Map<String, Object> model, List<String> report) {
        /**
         * The configuration as YAML, the report as comments at its top.
         *
         * @return the text
         */
        public String yaml() {
            StringBuilder text = new StringBuilder("# Converted from a CosId 4.x configuration by cosid-migration config (09-migration.md §3).\n");
            if (!report.isEmpty()) {
                text.append("# Not converted as is - review before starting 5.0:\n");
                report.forEach(line -> text.append("#  - ").append(line).append('\n'));
            }
            try {
                text.append(YAML.writeValueAsString(model));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            return text.toString();
        }

        /**
         * Writes {@link #yaml()} to a file.
         *
         * @param file the file
         */
        public void write(Path file) {
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, yaml());
            } catch (IOException e) {
                throw new UncheckedIOException("Writing " + file + " failed", e);
            }
        }
    }

    /**
     * Converts a configuration.
     *
     * @param config the 4.x configuration
     * @param result the apply result, for {@code not-before}; null when not applied yet
     * @return the conversion
     */
    public Conversion convert(FlatConfig config, ApplyResult result) {
        Legacy4x legacy = new Legacy4x(config);
        final List<String> report = new ArrayList<>();
        Set<String> handled = new LinkedHashSet<>();
        Map<String, Object> cosid = new LinkedHashMap<>();
        cosid.put("namespace", legacy.namespace());
        handled.add("cosid.namespace");
        if (!config.bool("cosid.enabled", true)) {
            cosid.put("enabled", false);
        }
        handled.add("cosid.enabled");
        if (result != null && !result.namespace().equals(legacy.namespace())) {
            report.add("apply-result.json is of namespace [" + result.namespace() + "], the configuration of [" + legacy.namespace() + "]");
        }

        Map<String, Map<String, Object>> stores = new LinkedHashMap<>();
        String segmentStore = legacy.segments().isEmpty() ? null : store(config, "cosid.segment.distributor", "redis", stores, report, handled);
        Map<String, Object> generators = new LinkedHashMap<>();
        legacy.snowflakes().forEach((name, prefix) -> generators.put(name, snowflake(legacy, name, prefix, report, handled)));
        legacy.segments().forEach((name, prefix) -> generators.put(name, segment(legacy, name, prefix, segmentStore, report, handled)));
        if (legacy.cosidEnabled()) {
            if (generators.containsKey("cosid")) {
                report.add("cosid.generator: a generator named [cosid] exists already; the CosIdGenerator is written as [cosid80]");
            }
            generators.put(generators.containsKey("cosid") ? "cosid80" : "cosid", cosid80(config, report, handled));
        }
        if (legacy.snowflakeEnabled() && legacy.segmentEnabled() && legacy.snowflakes().containsKey(Legacy4x.DEFAULT)
            && legacy.segments().containsKey(Legacy4x.SEGMENT_SHARE)) {
            report.add("both 4.x shares are enabled: the snowflake share is [default], the segment share [" + Legacy4x.SEGMENT_SHARE
                + "] (plan maps __share__ the same way with --layout-config)");
        }
        handled.add("cosid.snowflake.enabled");
        handled.add("cosid.snowflake.share.enabled");
        handled.add("cosid.segment.enabled");
        handled.add("cosid.segment.share.enabled");
        handled.add("cosid.generator.enabled");

        boolean timeBased = !legacy.snowflakes().isEmpty() || legacy.cosidEnabled();
        final Map<String, Object> machine = timeBased ? machine(legacy, result, stores, report, handled) : null;
        if (!timeBased && legacy.machineEnabled()) {
            report.add("cosid.machine: no time-based generator, so 5.0 needs no machine slot; the machine settings are dropped");
            handled.addAll(config.keys("cosid.machine"));
        }
        handled.add("cosid.machine.enabled");
        if (!stores.isEmpty()) {
            cosid.put("stores", stores);
        }
        if (machine != null) {
            cosid.put("machine", machine);
        }
        cosid.put("generators", generators);
        if (config.get("cosid.metrics.generated.enabled") != null) {
            cosid.put("metrics", Map.of("generated", config.bool("cosid.metrics.generated.enabled", false)));
            handled.add("cosid.metrics.generated.enabled");
        }

        for (String key : config.keys()) {
            if (key.startsWith("cosid.") && !handled.contains(key) && !covered(key, handled)) {
                report.add(key + " = " + config.get(key) + ": no 5.0 counterpart, dropped");
            }
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("cosid", cosid);
        return new Conversion(model, report);
    }

    private static boolean covered(String key, Set<String> handled) {
        for (String done : handled) {
            if (done.endsWith(".*") && key.startsWith(done.substring(0, done.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The 5.0 store of a 4.x distributor, named after its type.
     *
     * @return the store name, or null for a type 5.0 has no store for here
     */
    private static String store(FlatConfig config, String prefix, String defaultType, Map<String, Map<String, Object>> stores, List<String> report,
                                Set<String> handled) {
        String type = config.get(prefix + ".type", defaultType).toLowerCase(Locale.ROOT);
        handled.add(prefix + ".type");
        if (!STORE_TYPES.contains(type)) {
            report.add(prefix + ".type = " + type + ": no 5.0 store converts from it (proxy: the remote store of R4-A); configure cosid.stores by hand");
            handled.add(prefix + ".*");
            return null;
        }
        Map<String, Object> store = stores.computeIfAbsent(type, name -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("type", name);
            return created;
        });
        String timeoutKey = prefix + "." + type + "." + ("jdbc".equals(type) ? "query-timeout" : "timeout");
        String timeout = config.get(timeoutKey);
        if (timeout != null) {
            Object existing = store.putIfAbsent("timeout", timeout);
            if (existing != null && !existing.equals(timeout)) {
                report.add(timeoutKey + " = " + timeout + ": store [" + type + "] keeps the timeout " + existing);
            }
        }
        handled.add(timeoutKey);
        Map<String, Object> options = new LinkedHashMap<>();
        switch (type) {
            case "redis" -> {
                if (config.number(prefix + ".redis.wait-replicas", 0) > 0) {
                    options.put("wait", "auto");
                    report.add(prefix + ".redis.wait-replicas: 5.0 waits for the replicas it finds (wait: auto); 4.x wait-timeout is dropped");
                }
                handled.add(prefix + ".redis.*");
            }
            case "mongo" -> {
                String database = config.get(prefix + ".mongo.database");
                if (database != null && !"cosid_db".equals(database)) {
                    options.put("database", database);
                }
                if (config.get(prefix + ".mongo.write-concern") != null) {
                    report.add(prefix + ".mongo.write-concern: 5.0 always uses majority read and write concerns");
                }
                handled.add(prefix + ".mongo.*");
            }
            case "zookeeper" -> {
                options.put("connectString", config.get("cosid.zookeeper.connect-string", "localhost:2181"));
                copy(config, "cosid.zookeeper.session-timeout", "sessionTimeout", options);
                copy(config, "cosid.zookeeper.connection-timeout", "connectionTimeout", options);
                copy(config, "cosid.zookeeper.block-until-connected-wait", "blockUntilConnectedWait", options);
                for (String key : config.keys("cosid.zookeeper.retry")) {
                    report.add(key + " = " + config.get(key) + ": set the store option retry." + key.substring("cosid.zookeeper.retry.".length())
                        + " by hand (Curator retry, 06 §2.4)");
                }
                handled.add("cosid.zookeeper.*");
                handled.add(prefix + ".zookeeper.*");
            }
            default -> {
                for (String key : config.keys(prefix + ".jdbc")) {
                    if (!key.endsWith("query-timeout")) {
                        report.add(key + ": custom 4.x SQL and table initialization are gone; 5.0 uses JdbcDialect and options.initSchema (06 §2.1)");
                    }
                }
                handled.add(prefix + ".jdbc.*");
            }
        }
        if (!options.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) store.computeIfAbsent("options", name -> new LinkedHashMap<String, Object>());
            existing.putAll(options);
        }
        return type;
    }

    private static void copy(FlatConfig config, String key, String option, Map<String, Object> options) {
        if (config.get(key) != null) {
            options.put(option, config.get(key));
        }
    }

    private Map<String, Object> machine(Legacy4x legacy, ApplyResult result, Map<String, Map<String, Object>> stores, List<String> report, Set<String> handled) {
        FlatConfig config = legacy.config();
        final String type = config.get("cosid.machine.distributor.type", "manual").toLowerCase(Locale.ROOT).replace('-', '_');
        handled.add("cosid.machine.distributor.type");
        handled.add("cosid.machine.machine-bit");
        if (!legacy.machineEnabled()) {
            report.add("cosid.machine.enabled is false, but 5.0 time-based generators need a slot: a static slot is written");
        }
        for (String dropped : List.of("cosid.machine.guarder", "cosid.machine.clock-backwards", "cosid.machine.state-storage")) {
            for (String key : config.keys(dropped)) {
                report.add(key + " = " + config.get(key) + ": replaced by the lease window (05); dropped");
            }
            handled.add(dropped + ".*");
        }
        if (config.get("cosid.machine.stable") != null) {
            report.add("cosid.machine.stable: stable leases are gone; the slot an owner key held last is preferred instead (05 §2.1)");
            handled.add("cosid.machine.stable");
        }
        if (config.get("cosid.machine.port") != null) {
            report.add("cosid.machine.port = " + config.get("cosid.machine.port") + ": set owner-key to host:port if the slot should follow it");
            handled.add("cosid.machine.port");
        }
        final Map<String, Object> machine = new LinkedHashMap<>();
        int bits = legacy.machineBit();
        if ("manual".equals(type) || "stateful_set".equals(type) || !legacy.machineEnabled()) {
            Map<String, Object> fixed = new LinkedHashMap<>();
            fixed.put("bits", bits);
            if ("stateful_set".equals(type)) {
                fixed.put("slot", "${POD_ORDINAL}");
                report.add("cosid.machine.distributor.type = stateful_set: the slot is ${POD_ORDINAL}; set it from the pod ordinal");
            } else if (config.get("cosid.machine.distributor.manual.machine-id") != null) {
                fixed.put("slot", config.number("cosid.machine.distributor.manual.machine-id", 0));
            } else {
                fixed.put("slot", "${COSID_SLOT}");
                report.add("cosid.machine.distributor.manual.machine-id is not set: the slot is ${COSID_SLOT}");
            }
            handled.add("cosid.machine.distributor.manual.machine-id");
            if (result != null) {
                fixed.put("not-before", result.notBefore());
            } else {
                report.add("cosid.machine.static.not-before: pass --result apply-result.json; without it the static slot does not start after the 4.x timestamps (09 §4.4)");
            }
            report.add("the 4.x machine state file is not migrated; set cosid.machine.static.state-file to a new file to keep clock-regression protection (05 §4.4)");
            machine.put("static", fixed);
            return machine;
        }
        String store = store(config, "cosid.machine.distributor", "manual", stores, report, handled);
        if (store == null) {
            return null;
        }
        Map<String, Object> leased = new LinkedHashMap<>();
        leased.put("store", store);
        leased.put("bits", bits);
        if (config.get("cosid.machine.instance-id") != null) {
            leased.put("owner-key", config.get("cosid.machine.instance-id"));
            handled.add("cosid.machine.instance-id");
        }
        machine.put("leased", leased);
        return machine;
    }

    private Map<String, Object> snowflake(Legacy4x legacy, String name, String prefix, List<String> report, Set<String> handled) {
        final FlatConfig config = legacy.config();
        Map<String, Object> generator = new LinkedHashMap<>();
        generator.put("strategy", "snowflake");
        Layout layout = legacy.snowflakeLayout(prefix);
        String preset = Legacy4x.preset(layout);
        if (preset != null) {
            generator.put("layout", preset);
        } else {
            Map<String, Object> custom = new LinkedHashMap<>();
            custom.put("epoch", layout.epoch().toString());
            custom.put("unit", layout.unit().name());
            custom.put("time-bits", layout.timeBits());
            custom.put("node-bits", layout.nodeBits());
            custom.put("seq-bits", layout.seqBits());
            custom.put("seq-reset-threshold", layout.seqResetThreshold());
            generator.put("layout", custom);
            report.add(prefix + ": the 4.x layout is no 5.0 preset; it is written field by field, keep it as is (M3)");
        }
        for (String part : List.of("epoch", "timestamp-unit", "timestamp-bit", "machine-bit", "sequence-bit", "sequence-reset-threshold", "enabled")) {
            handled.add(prefix + "." + part);
        }
        handled.add("cosid.snowflake.epoch");
        if (config.get(prefix + ".clock-sync") != null) {
            report.add(prefix + ".clock-sync: replaced by the clock regression policy (borrow 5ms, wait 1s; 03 §4)");
            handled.add(prefix + ".clock-sync");
        }
        ownNamespace(config, prefix, handled);
        generator.put("codec", codecs(config, prefix + ".converter", true, report, handled));
        handled.add("cosid.snowflake.zone-id");
        return generator;
    }

    private static void ownNamespace(FlatConfig config, String prefix, Set<String> handled) {
        if (config.get(prefix + ".namespace") != null) {
            handled.add(prefix + ".namespace");
        }
    }

    private Map<String, Object> segment(Legacy4x legacy, String name, String prefix, String store, List<String> report, Set<String> handled) {
        final FlatConfig config = legacy.config();
        Map<String, Object> generator = new LinkedHashMap<>();
        String period = legacy.period(prefix);
        generator.put("strategy", period == null ? "segment" : "periodic-segment");
        if (store != null) {
            generator.put("store", store);
        }
        generator.put("step", config.number(prefix + ".step", 10));
        handled.add(prefix + ".step");
        handled.add(prefix + ".enabled");
        if (config.number(prefix + ".offset", 0) != 0) {
            report.add(prefix + ".offset = " + config.get(prefix + ".offset") + ": migrated keys continue from their 4.x values; offset only applied to new 4.x keys");
        }
        handled.add(prefix + ".offset");
        for (String dropped : List.of(prefix + ".mode", prefix + ".ttl", prefix + ".chain")) {
            for (String key : config.keys(dropped)) {
                report.add(key + " = " + config.get(key) + ": segment chains are replaced by the 5.0 refill (lookahead, adaptive batch; 04)");
            }
            handled.add(dropped + ".*");
            handled.add(dropped);
        }
        for (String key : config.keys("cosid.segment")) {
            if (key.startsWith("cosid.segment.mode") || key.startsWith("cosid.segment.ttl") || key.startsWith("cosid.segment.chain")) {
                if (handled.add(key)) {
                    report.add(key + " = " + config.get(key) + ": segment chains are replaced by the 5.0 refill (lookahead, adaptive batch; 04)");
                }
            }
        }
        ownNamespace(config, prefix, handled);
        List<Object> codec = codecs(config, prefix + ".converter", false, report, handled);
        if (period != null) {
            generator.put("period", period);
            generator.put("zone", zone().getId());
            report.add(prefix + ".group: 4.x used the server's default time zone; the 5.0 period zone is " + zone().getId() + ", check it (--zone)");
            String pattern = config.get(prefix + ".group.pattern");
            String presetPattern = switch (period) {
                case "YEAR" -> "yyyy";
                case "YEAR_MONTH" -> "yyyyMM";
                default -> "yyyyMMdd";
            };
            if (pattern != null && !pattern.equals(presetPattern)) {
                report.add(prefix + ".group.pattern = " + pattern + ": 5.0 period keys are " + presetPattern
                    + "; plan reformats the 4.x keys, and grouped string ids now carry the " + presetPattern + " prefix");
            }
            handled.add(prefix + ".group.by");
            handled.add(prefix + ".group.pattern");
            List<Object> grouped = new ArrayList<>();
            boolean groupPrefix = config.bool(prefix + ".converter.group-prefix.enabled", false);
            if (!groupPrefix) {
                report.add(prefix + ".converter.group-prefix is off: 5.0 grouped string ids always start with the period");
            }
            grouped.add(Map.of("date-prefix", Map.of("delimiter", text(config, prefix + ".converter.group-prefix.delimiter", "-"))));
            if (!config.bool(prefix + ".converter.group-prefix.before-prefix", true)) {
                report.add(prefix + ".converter.group-prefix.before-prefix = false: the 5.0 period prefix always comes first");
            }
            handled.add(prefix + ".converter.group-prefix.*");
            grouped.addAll(codec);
            generator.put("codec", grouped);
        } else {
            generator.put("codec", codec);
        }
        return generator;
    }

    private ZoneId zone() {
        return zone == null ? ZoneId.systemDefault() : zone;
    }

    private List<Object> codecs(FlatConfig config, String prefix, boolean snowflake, List<String> report, Set<String> handled) {
        List<Object> codec = new ArrayList<>();
        String type = config.get(prefix + ".type", "radix").toLowerCase(Locale.ROOT).replace('-', '_');
        handled.add(prefix + ".type");
        switch (type) {
            case "radix" -> codec.add(radix(62, config.number(prefix + ".radix.char-size", 11), config.bool(prefix + ".radix.pad-start", true)));
            case "radix36" -> codec.add(radix(36, config.number(prefix + ".radix36.char-size", 13), config.bool(prefix + ".radix36.pad-start", true)));
            case "to_string" -> {
                if (config.bool(prefix + ".to-string.pad-start", false)) {
                    codec.add(radix(10, config.number(prefix + ".to-string.char-size", 11), true));
                }
            }
            case "snowflake_friendly" -> {
                if (snowflake) {
                    Map<String, Object> friendly = new LinkedHashMap<>();
                    friendly.put("zone", config.get("cosid.snowflake.zone-id", zone().getId()));
                    if (config.bool(prefix + ".friendly.pad-start", false)) {
                        friendly.put("pad-start", true);
                    }
                    codec.add(Map.of("friendly", friendly));
                    if (config.get("cosid.snowflake.zone-id") == null) {
                        report.add(prefix + ": the friendly codec used the server's default time zone; it is " + zone().getId() + " now (--zone)");
                    }
                } else {
                    report.add(prefix + ".type = snowflake_friendly: only snowflake ids have a friendly form; dropped");
                }
            }
            case "custom" -> {
                String custom = config.get(prefix + ".custom.type", "");
                codec.add(Map.of("custom", custom));
                report.add(prefix + ".custom.type = " + custom + ": a 4.x IdConverter; port it to a 5.0 IdCodec");
            }
            default -> report.add(prefix + ".type = " + type + ": unknown converter; dropped");
        }
        for (String part : List.of("radix", "radix36", "to-string", "friendly", "custom")) {
            handled.add(prefix + "." + part + ".*");
        }
        if (config.bool(prefix + ".date-prefix.enabled", false)) {
            Map<String, Object> datePrefix = new LinkedHashMap<>();
            datePrefix.put("pattern", config.get(prefix + ".date-prefix.pattern", "yyMMdd"));
            datePrefix.put("delimiter", text(config, prefix + ".date-prefix.delimiter", "-"));
            codec.add(Map.of("date-prefix", datePrefix));
        }
        handled.add(prefix + ".date-prefix.*");
        String text = config.get(prefix + ".prefix");
        if (text != null) {
            codec.add(Map.of("prefix", text));
        }
        String suffix = config.get(prefix + ".suffix");
        if (suffix != null) {
            codec.add(Map.of("suffix", suffix));
        }
        handled.add(prefix + ".prefix");
        handled.add(prefix + ".suffix");
        if (!config.bool(prefix + ".date-prefix.before-prefix", true) && text != null) {
            report.add(prefix + ".date-prefix.before-prefix = false: the 5.0 codec list applies in order; check the order of date-prefix and prefix");
        }
        return codec;
    }

    /** A text value where the empty text is a value of its own, such as a delimiter. */
    private static String text(FlatConfig config, String key, String fallback) {
        String value = config.get(key);
        return value == null ? fallback : value;
    }

    private static Map<String, Object> radix(int radix, long width, boolean padStart) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("radix", radix);
        spec.put("width", width);
        spec.put("pad-start", padStart);
        return Map.of("radix", spec);
    }

    private Map<String, Object> cosid80(FlatConfig config, List<String> report, Set<String> handled) {
        Map<String, Object> generator = new LinkedHashMap<>();
        generator.put("strategy", "cosid80");
        String type = config.get("cosid.generator.type", "radix62").toLowerCase(Locale.ROOT);
        switch (type) {
            case "radix36" -> generator.put("codec", Map.of("radix", 36));
            case "friendly" -> generator.put("codec", Map.of("friendly", Map.of("zone", config.get("cosid.generator.zone-id", zone().getId()))));
            default -> generator.put("codec", Map.of("radix", 62));
        }
        if (!new Legacy4x(config).cosidLayout().equals(Layout.COSID_80)) {
            report.add("cosid.generator: the 4.x bits differ from the 5.0 COSID_80 layout (44/20/16); the 5.0 ids keep COSID_80 (M3 fails in plan)");
        }
        if (!config.bool("cosid.generator.pad-start", true)) {
            report.add("cosid.generator.pad-start = false: 5.0 cosid80 text ids are always padded");
        }
        for (String part : List.of("type", "zone-id", "timestamp-bit", "machine-bit", "sequence-bit", "sequence-reset-threshold", "pad-start", "namespace")) {
            handled.add("cosid.generator." + part);
        }
        return generator;
    }

    /**
     * The 5.0 layouts {@link #convert} gives the time-based generators of a 4.x configuration: each snowflake keeps its
     * 4.x layout, the CosIdGenerator becomes {@link Layout#COSID_80}.
     *
     * @param legacy the 4.x configuration
     * @return 5.0 generator name to layout
     */
    public static Map<String, Layout> targetLayouts(Legacy4x legacy) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        legacy.snowflakes().forEach((name, prefix) -> layouts.put(name, legacy.snowflakeLayout(prefix)));
        if (legacy.cosidEnabled()) {
            layouts.put("cosid", Layout.COSID_80);
        }
        return layouts;
    }

    /**
     * The layouts of the time-based generators of a 5.0 configuration ({@code plan --target-config}).
     *
     * @param config the 5.0 configuration
     * @return generator name to layout
     */
    public static Map<String, Layout> layoutsOf5(FlatConfig config) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        for (String name : config.names("cosid.generators")) {
            String prefix = "cosid.generators." + name;
            String strategy = config.get(prefix + ".strategy", "");
            if ("cosid80".equals(strategy)) {
                layouts.put(name, Layout.COSID_80);
            } else if ("snowflake".equals(strategy)) {
                String preset = config.get(prefix + ".layout", "SNOWFLAKE_MS");
                if (config.get(prefix + ".layout.time-bits") != null) {
                    int seqBits = (int) config.number(prefix + ".layout.seq-bits", 12);
                    layouts.put(name, new Layout(Instant.parse(config.get(prefix + ".layout.epoch", "2019-12-24T16:00:00Z")),
                        ChronoUnit.valueOf(config.get(prefix + ".layout.unit", "MILLIS").toUpperCase(Locale.ROOT)),
                        (int) config.number(prefix + ".layout.time-bits", 41), (int) config.number(prefix + ".layout.node-bits", 10), seqBits,
                        config.number(prefix + ".layout.seq-reset-threshold", Layout.defaultSeqResetThreshold(seqBits))));
                } else {
                    layouts.put(name, switch (preset.toUpperCase(Locale.ROOT)) {
                        case "SNOWFLAKE_S" -> Layout.SNOWFLAKE_S;
                        case "JS_SAFE_MS" -> Layout.JS_SAFE_MS;
                        case "JS_SAFE_S" -> Layout.JS_SAFE_S;
                        default -> Layout.SNOWFLAKE_MS;
                    });
                }
            }
        }
        return layouts;
    }
}
