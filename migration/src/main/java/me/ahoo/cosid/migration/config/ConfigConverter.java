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
import me.ahoo.cosid.migration.Plan;
import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.store.StoreOptions;
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
 * as far as it goes; whatever has no 5.0 counterpart, or changes behaviour, goes to the report, one line per key. With
 * {@code apply-result.json}, the stores are those apply wrote to ({@code target}, {@code machineTarget}; R2-mig-01),
 * each migrated segment starts new keys at its migrated next value, and the StaticSlot {@code not-before} is W
 * (09 §4.4).
 */
public final class ConfigConverter {
    private static final YAMLMapper YAML = new YAMLMapper(new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS));
    private static final Set<String> STORE_TYPES = Set.of("jdbc", "redis", "mongo", "zookeeper");
    /** The client a 5.0 Spring application gives each store type (CosIdStoreClients), for the report. */
    private static final Map<String, String> CLIENTS = Map.of("jdbc", "a DataSource", "redis", "a Lettuce client or RedisConnectionFactory",
        "mongo", "a MongoClient", "zookeeper", "a CuratorFramework or options.connectString");
    /** The report line of the migrated next values written as {@code initial-offset}. */
    static final String MIGRATED_OFFSETS = "initial-offset of each migrated segment is its next value from apply-result.json: should 5.0 open another"
        + " store than apply wrote to, its keys start there, leaving a gap instead of issuing the 4.x ids again";
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
         * The segment and periodic segment generators that continue no key of an apply result: a plain generator needs
         * {@code {ns}/{name}}, a periodic one some {@code {ns}/{name}/{period}} (rc-review D-B2).
         *
         * @param result the apply result; null for none, so every segment generator
         * @return the generator names
         */
        @SuppressWarnings("unchecked")
        public List<String> unmigratedSegments(ApplyResult result) {
            Map<String, Object> cosid = (Map<String, Object>) model.get("cosid");
            String namespace = String.valueOf(cosid.get("namespace"));
            Map<String, Object> generators = (Map<String, Object>) cosid.getOrDefault("generators", Map.of());
            List<String> keys = result == null ? List.of() : result.segments().stream().map(ApplyResult.SegmentResult::targetKey).toList();
            List<String> missing = new ArrayList<>();
            generators.forEach((name, spec) -> {
                Object strategy = ((Map<String, Object>) spec).get("strategy");
                if ("segment".equals(strategy) && !keys.contains(namespace + "/" + name)) {
                    missing.add(name);
                } else if ("periodic-segment".equals(strategy) && keys.stream().noneMatch(key -> key.startsWith(namespace + "/" + name + "/"))) {
                    missing.add(name);
                }
            });
            return missing;
        }

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
        // R2-mig-01: the stores apply wrote to, not the 4.x ones; a result of 5.0.0-rc.1 without them keeps the 4.x types.
        Map<String, StoreLocation> placed = new LinkedHashMap<>();
        StoreLocation source = result == null ? null : result.source();
        StoreLocation segmentTarget = result == null ? null : result.target();
        String segmentStore = legacy.segments().isEmpty() ? null
            : store(config, "cosid.segment.distributor", "redis", new Placement(segmentTarget, source, placed), stores, report, handled);
        final Map<String, Legacy4x.MachineNamespace> machineNamespaces = legacy.machineNamespaces();
        Map<String, Object> generators = new LinkedHashMap<>();
        legacy.snowflakes().forEach((name, prefix) -> generators.put(name, snowflake(legacy, name, prefix, machineNamespaces, report, handled)));
        // The names plan and apply gave the migrated keys (--rename, the share): the generators must continue them (D-B2).
        Map<String, String> renames = result == null || result.renames() == null ? Map.of() : result.renames();
        legacy.segments().forEach((name, prefix) -> {
            String renamed = renames.get(prefix.equals("cosid.segment.share") ? Legacy4x.SHARE : name);
            String generator = renamed == null ? name : renamed;
            generators.put(generator, segment(legacy, name, prefix, segmentStore, migratedNext(result, generator), report, handled));
        });
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
        StoreLocation machineTarget = result == null ? null : result.machineTarget() != null ? result.machineTarget() : result.target();
        final Map<String, Object> machine = timeBased ? machine(legacy, result, new Placement(machineTarget, source, placed), stores, report, handled) : null;
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
            if (!machineNamespaces.isEmpty()) {
                Map<String, Object> machines = new LinkedHashMap<>();
                machineNamespaces.values().forEach(own -> machines.put(own.machine(), namedMachine(legacy, own, machine, result, report)));
                cosid.put("machines", machines);
            }
        }
        cosid.put("generators", generators);
        if (config.get("cosid.metrics.generated.enabled") != null) {
            cosid.put("metrics", Map.of("generated", Map.of("enabled", config.bool("cosid.metrics.generated.enabled", false))));
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
     * Where a store goes: the location apply wrote to, if known, and the 4.x store it came from.
     *
     * @param target the 5.0 store apply wrote to; null without an apply result, or one of 5.0.0-rc.1
     * @param source the 4.x store of the segments; null without an apply result
     * @param placed the stores written so far, by name
     */
    private record Placement(StoreLocation target, StoreLocation source, Map<String, StoreLocation> placed) {
    }

    /**
     * The 5.0 store of a 4.x distributor, named after its type: the type apply wrote to when the result has it
     * (R2-mig-01), and the 4.x options where the type is the same.
     *
     * @return the store name, or null for a type 5.0 has no store for here
     */
    private static String store(FlatConfig config, String prefix, String defaultType, Placement placement, Map<String, Map<String, Object>> stores,
                                List<String> report, Set<String> handled) {
        final String legacyType = config.get(prefix + ".type", defaultType).toLowerCase(Locale.ROOT);
        handled.add(prefix + ".type");
        if ("proxy".equals(legacyType)) {
            // The 4.x proxy becomes the 5.0 remote store (08, store/remote): cosid.proxy.* map to its options.
            stores.computeIfAbsent("remote", name -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("type", "remote");
                Map<String, Object> options = new LinkedHashMap<>();
                options.put("baseUrl", config.get("cosid.proxy.host", "http://localhost:8688"));
                copy(config, "cosid.proxy.token", "token", options);
                copy(config, "cosid.proxy.connect-timeout", "connectTimeout", options);
                copy(config, "cosid.proxy.read-timeout", "requestTimeout", options);
                created.put("options", options);
                return created;
            });
            if (handled.add("cosid.proxy.*")) {
                report.add(prefix + ".type = proxy: written as store [remote] (store/remote, 08); its baseUrl must point at a 5.0 cosid-server,"
                    + " the 4.x proxy server does not speak the 5.0 API");
                if (config.get("cosid.proxy.client") != null) {
                    report.add("cosid.proxy.client = " + config.get("cosid.proxy.client") + ": the 5.0 remote store uses the JDK HttpClient; dropped");
                }
            }
            handled.add(prefix + ".*");
            return "remote";
        }
        if (!STORE_TYPES.contains(legacyType)) {
            report.add(prefix + ".type = " + legacyType + ": no 5.0 store converts from it; configure cosid.stores by hand");
            handled.add(prefix + ".*");
            return null;
        }
        StoreLocation target = placement.target();
        final String type = target == null ? legacyType : target.type();
        String name = type;
        if (target != null) {
            StoreLocation earlier = placement.placed().putIfAbsent(name, target);
            if (earlier != null && !StoreLocation.redactUrl(earlier.url()).equals(StoreLocation.redactUrl(target.url()))) {
                // The slots went to another store of the same type than the segments (--machine-to).
                name = type + "-machine";
                placement.placed().put(name, target);
            }
        }
        Map<String, Object> store = stores.computeIfAbsent(name, created -> {
            Map<String, Object> fresh = new LinkedHashMap<>();
            fresh.put("type", type);
            return fresh;
        });
        String timeoutKey = prefix + "." + legacyType + "." + ("jdbc".equals(legacyType) ? "query-timeout" : "timeout");
        String timeout = config.get(timeoutKey);
        if (timeout != null) {
            Object existing = store.putIfAbsent("timeout", timeout);
            if (existing != null && !existing.equals(timeout)) {
                report.add(timeoutKey + " = " + timeout + ": store [" + name + "] keeps the timeout " + existing);
            }
        }
        handled.add(timeoutKey);
        Map<String, Object> options = new LinkedHashMap<>();
        if (type.equals(legacyType)) {
            legacyOptions(config, prefix, type, options, report, handled);
        } else {
            report.add(prefix + ".type = " + legacyType + ": apply wrote to " + target + ", so store [" + name + "] is of type " + type + "; the 5.0"
                + " application needs cosid-store-" + type + " and " + CLIENTS.get(type) + " on that store, not the 4.x " + legacyType + " client");
            for (String key : config.keys(prefix + "." + legacyType)) {
                if (!key.equals(timeoutKey)) {
                    report.add(key + " = " + config.get(key) + ": an option of the 4.x " + legacyType + " store, not of the " + type + " target; dropped");
                }
            }
            handled.add(prefix + "." + legacyType + ".*");
            if ("zookeeper".equals(legacyType) && handled.add("cosid.zookeeper.*")) {
                for (String key : config.keys("cosid.zookeeper")) {
                    report.add(key + " = " + config.get(key) + ": an option of the 4.x zookeeper store, not of the " + type + " target; dropped");
                }
            }
        }
        if (target != null) {
            targetOptions(target, options);
            StoreLocation source = placement.source();
            if (type.equals(legacyType) && (source == null || !StoreLocation.redactUrl(source.url()).equals(StoreLocation.redactUrl(target.url())))
                && !"zookeeper".equals(type)) {
                report.add("store [" + name + "]: apply wrote to " + target + ", not to the 4.x store; " + CLIENTS.get(type) + " of the 5.0 application"
                    + " must reach it");
            }
        }
        if (!options.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) store.computeIfAbsent("options", created -> new LinkedHashMap<String, Object>());
            existing.putAll(options);
        }
        return name;
    }

    /** The options of a 4.x store as the 5.0 store of the same type names them (R2-mig-04). */
    private static void legacyOptions(FlatConfig config, String prefix, String type, Map<String, Object> options, List<String> report, Set<String> handled) {
        switch (type) {
            case "redis" -> {
                long replicas = config.number(prefix + ".redis.wait-replicas", 0);
                if (replicas > 0) {
                    // WAIT for that many replicas, as 4.x did; auto would wait for one (06 §2.2).
                    options.put("wait", replicas);
                }
                if (config.get(prefix + ".redis.wait-timeout") != null) {
                    report.add(prefix + ".redis.wait-timeout = " + config.get(prefix + ".redis.wait-timeout") + ": 5.0 waits for the replicas within the"
                        + " store timeout; dropped");
                }
                handled.add(prefix + ".redis.*");
            }
            case "mongo" -> {
                String database = config.get(prefix + ".mongo.database");
                if (database != null && !"cosid_db".equals(database)) {
                    options.put("database", database);
                }
                copy(config, prefix + ".mongo.write-concern", "writeConcern", options);
                handled.add(prefix + ".mongo.*");
            }
            case "zookeeper" -> {
                options.put("connectString", config.get("cosid.zookeeper.connect-string", "localhost:2181"));
                copy(config, "cosid.zookeeper.session-timeout", "sessionTimeout", options);
                copy(config, "cosid.zookeeper.connection-timeout", "connectionTimeout", options);
                copy(config, "cosid.zookeeper.block-until-connected-wait", "blockUntilConnectedWait", options);
                // The Curator retry of 4.x in milliseconds, as the 5.0 options name it (06 §2.4).
                Map<String, String> retry = new LinkedHashMap<>();
                retry.put("base-sleep-time-ms", "retry.baseSleepTime");
                retry.put("max-retries", "retry.maxRetries");
                retry.put("max-sleep-ms", "retry.maxSleep");
                for (String key : config.keys("cosid.zookeeper.retry")) {
                    String option = retry.get(key.substring("cosid.zookeeper.retry.".length()));
                    if (option == null) {
                        report.add(key + " = " + config.get(key) + ": no 5.0 Curator retry option; dropped");
                    }
                }
                retry.forEach((legacy, option) -> {
                    String value = config.get("cosid.zookeeper.retry." + legacy);
                    if (value != null) {
                        options.put(option, legacy.endsWith("-ms") ? value + "ms" : config.number("cosid.zookeeper.retry." + legacy, 0));
                    }
                });
                handled.add("cosid.zookeeper.*");
                handled.add(prefix + ".zookeeper.*");
            }
            default -> {
                copy(config, prefix + ".jdbc.fetch-plan", "fetchPlan", options);
                for (String key : config.keys(prefix + ".jdbc")) {
                    if (!key.endsWith("query-timeout") && !key.endsWith(".fetch-plan")) {
                        report.add(key + ": custom 4.x SQL and table initialization are gone; 5.0 uses JdbcDialect and options.initSchema (06 §2.1)");
                    }
                }
                handled.add(prefix + ".jdbc.*");
            }
        }
    }

    /**
     * The options of the store apply wrote to that the 5.0 store needs to find the migrated data: the JDBC table
     * names, the Mongo database, Redis Cluster and the ZooKeeper connect string. Accounts and passwords stay out.
     */
    private static void targetOptions(StoreLocation target, Map<String, Object> options) {
        switch (target.type()) {
            case StoreLocation.JDBC -> {
                for (String table : List.of("rangeTable", "slotTable", "namespaceTable")) {
                    String value = targetOption(target, table);
                    if (value != null) {
                        options.put(table, value);
                    }
                }
            }
            case StoreLocation.MONGO -> {
                String database = targetOption(target, "database");
                if (database == null || "cosid_db".equals(database)) {
                    options.remove("database");
                } else {
                    options.put("database", database);
                }
            }
            case StoreLocation.REDIS -> {
                if (Boolean.parseBoolean(targetOption(target, "cluster"))) {
                    options.put("cluster", true);
                }
            }
            default -> options.put("connectString", target.url().substring("zookeeper://".length()));
        }
    }

    /** An option of a location, whatever its spelling ({@code range-table}, {@code RANGE_TABLE}). */
    private static String targetOption(StoreLocation target, String name) {
        for (Map.Entry<String, String> option : target.options().entrySet()) {
            if (StoreOptions.canonical(option.getKey()).equals(StoreOptions.canonical(name))) {
                return option.getValue();
            }
        }
        return null;
    }

    /** The next value apply gave the key of a plain segment generator; null without one. */
    private static Long migratedNext(ApplyResult result, String generator) {
        if (result == null || result.segments() == null) {
            return null;
        }
        String key = result.namespace() + "/" + generator;
        return result.segments().stream().filter(segment -> segment.targetKey().equals(key)).map(ApplyResult.SegmentResult::targetNext).findFirst()
            .orElse(null);
    }

    private static void copy(FlatConfig config, String key, String option, Map<String, Object> options) {
        if (config.get(key) != null) {
            options.put(option, config.get(key));
        }
    }

    private Map<String, Object> machine(Legacy4x legacy, ApplyResult result, Placement placement, Map<String, Map<String, Object>> stores, List<String> report,
                                        Set<String> handled) {
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
        // The slot parameters apply recorded (--slot-bits, --tolerance): 5.0 refuses others (NamespaceMismatchException).
        int bits = result == null ? legacy.machineBit() : result.slots().bits();
        final long tolerance = result == null ? 1000 : result.slots().toleranceMillis();
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
        String store = store(config, "cosid.machine.distributor", "manual", placement, stores, report, handled);
        if (store == null) {
            return null;
        }
        Map<String, Object> leased = new LinkedHashMap<>();
        leased.put("store", store);
        leased.put("bits", bits);
        if (tolerance != 1000) {
            leased.put("tolerance", tolerance + "ms");
        }
        if (config.get("cosid.machine.instance-id") != null) {
            leased.put("owner-key", config.get("cosid.machine.instance-id"));
            handled.add("cosid.machine.instance-id");
        }
        machine.put("leased", leased);
        return machine;
    }

    /**
     * The named machine of a namespace of its own (D37, api-freeze.md §2.7): the shared {@code cosid.generator.namespace}
     * or a snowflake's (R2-parity-01). The store and kind of the default machine, the slot parameters the namespace
     * was migrated with, as this service's apply found them.
     */
    private static Map<String, Object> namedMachine(Legacy4x legacy, Legacy4x.MachineNamespace own, Map<String, Object> machine, ApplyResult result,
                                                    List<String> report) {
        String shared = own.namespace();
        String key = "cosid.machines." + own.machine();
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("namespace", shared);
        if (!Legacy4x.GENERATOR_MACHINE.equals(own.machine()) || own.owners().size() > 1) {
            report.add(own.label() + ": 4.x distributed the machine ids of " + String.join(", ", own.owners()) + " in this namespace; written as the"
                + " named machine " + key + " (D37), each with machine: " + own.machine());
        }
        Plan.GeneratorNamespace recorded = null;
        if (result != null) {
            recorded = result.machineNamespaces() != null ? result.machineNamespaces().get(own.machine())
                : Legacy4x.GENERATOR_MACHINE.equals(own.machine()) ? result.generatorNamespace() : null;
        }
        if (recorded == null || !recorded.namespace().equals(shared)) {
            recorded = null;
        }
        int bits = recorded == null ? Math.min(legacy.machineBit(), own.nodeBits()) : recorded.bits();
        if (recorded == null) {
            report.add(key + ": no apply result of this service with the migrated shared namespace [" + shared + "]; written with"
                + " slot-offset 0 and " + bits + " bits. Migrate [" + shared + "] once (plan and apply --namespace " + shared + "), apply this service, and"
                + " run config with its apply-result.json");
        }
        if (machine.get("leased") instanceof Map<?, ?> defaultLeased) {
            Map<String, Object> leased = new LinkedHashMap<>();
            leased.put("store", defaultLeased.get("store"));
            leased.put("bits", bits);
            if (recorded != null && recorded.toleranceMillis() != 1000) {
                leased.put("tolerance", recorded.toleranceMillis() + "ms");
            }
            if (recorded != null && recorded.slotOffset() > 0) {
                leased.put("slot-offset", recorded.slotOffset());
            }
            named.put("leased", leased);
            report.add(key + ": every service sharing [" + shared + "] uses the same bits, tolerance and slot-offset; more peak pods"
                + " in all (rolling surge included) than 2^bits need more bits, within slot-offset + 2^bits <= 2^node-bits");
        } else {
            Map<String, Object> fixed = new LinkedHashMap<>();
            String slot = Legacy4x.GENERATOR_MACHINE.equals(own.machine()) ? "${COSID_GENERATOR_SLOT}"
                : "${COSID_" + own.machine().toUpperCase(Locale.ROOT).replace('-', '_') + "_SLOT}";
            if (bits != Legacy4x.DEFAULT_MACHINE_BIT) {
                fixed.put("bits", bits);
            }
            fixed.put("slot", slot);
            if (result != null) {
                fixed.put("not-before", result.notBefore());
            }
            named.put("static", fixed);
            report.add(key + ".static.slot is " + slot + ": a slot of [" + shared + "] unique across every service sharing it,"
                + " above the 4.x machine ids there");
        }
        return named;
    }

    private Map<String, Object> snowflake(Legacy4x legacy, String name, String prefix, Map<String, Legacy4x.MachineNamespace> machineNamespaces,
                                          List<String> report, Set<String> handled) {
        final FlatConfig config = legacy.config();
        Map<String, Object> generator = new LinkedHashMap<>();
        generator.put("strategy", "snowflake");
        // A namespace of its own: 4.x distributed this generator's machine id there, 5.0 has its named machine (R2-parity-01).
        String ownNamespace = config.get(prefix + ".namespace");
        Legacy4x.MachineNamespace own = ownNamespace == null ? null : machineNamespaces.get(ownNamespace);
        if (own != null) {
            generator.put("machine", own.machine());
        }
        handled.add(prefix + ".namespace");
        Layout layout = legacy.snowflakeLayout(prefix);
        String preset = Legacy4x.preset(layout);
        if (preset != null) {
            generator.put("layout", preset);
            // A 4.x threshold other than the preset's: set apart, the id space is the preset's (R1-x-04).
            long derived = layoutOfPreset(preset).seqResetThreshold();
            if (layout.seqResetThreshold() != derived) {
                generator.put("seq-reset-threshold", layout.seqResetThreshold());
            }
        } else {
            // A preset with field overrides, as the 5.0 binding takes them (epoch, unit, time-bits, node-bits, seq-bits).
            Layout base = layout.unit() == ChronoUnit.SECONDS ? Layout.SNOWFLAKE_S : Layout.SNOWFLAKE_MS;
            generator.put("layout", base == Layout.SNOWFLAKE_S ? "SNOWFLAKE_S" : "SNOWFLAKE_MS");
            if (!layout.epoch().equals(base.epoch())) {
                generator.put("epoch", layout.epoch().toString());
            }
            if (layout.timeBits() != base.timeBits()) {
                generator.put("time-bits", layout.timeBits());
            }
            if (layout.nodeBits() != base.nodeBits()) {
                generator.put("node-bits", layout.nodeBits());
            }
            if (layout.seqBits() != base.seqBits()) {
                generator.put("seq-bits", layout.seqBits());
            }
            // The threshold the 5.0 binding derives when seq-reset-threshold is not set.
            long derived = generator.containsKey("seq-bits") ? Layout.defaultSeqResetThreshold(layout.seqBits()) : base.seqResetThreshold();
            if (layout.seqResetThreshold() != derived) {
                generator.put("seq-reset-threshold", layout.seqResetThreshold());
            }
            report.add(prefix + ": the 4.x layout is no 5.0 preset; it is written as " + generator.get("layout") + " with overrides, keep it as is (M3)");
        }
        for (String part : List.of("epoch", "timestamp-unit", "timestamp-bit", "machine-bit", "sequence-bit", "sequence-reset-threshold", "enabled")) {
            handled.add(prefix + "." + part);
        }
        handled.add("cosid.snowflake.epoch");
        if (config.get(prefix + ".clock-sync") != null) {
            report.add(prefix + ".clock-sync: replaced by the clock regression policy (borrow 5ms, wait 1s; 03 §4)");
            handled.add(prefix + ".clock-sync");
        }
        generator.put("codec", codecs(config, prefix + ".converter", true, report, handled));
        handled.add("cosid.snowflake.zone-id");
        return generator;
    }

    private static void ownNamespace(FlatConfig config, String prefix, Set<String> handled) {
        if (config.get(prefix + ".namespace") != null) {
            handled.add(prefix + ".namespace");
        }
    }

    private Map<String, Object> segment(Legacy4x legacy, String name, String prefix, String store, Long migratedNext, List<String> report,
                                        Set<String> handled) {
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
        // 4.x issued offset + 1 first on a new key (a new period too); 5.0 issues initial-offset first, 1 by default (D21).
        // Migrated keys continue from their 4.x values either way: apply writes lastMaxId + 1.
        long offset = config.number(prefix + ".offset", 0);
        // R2-mig-01: a plain key starts at its migrated next value should 5.0 not find it (another store than apply wrote to).
        if (period == null && migratedNext != null && migratedNext > offset + 1) {
            generator.put("initial-offset", migratedNext);
            if (!report.contains(MIGRATED_OFFSETS)) {
                report.add(MIGRATED_OFFSETS);
            }
        } else if (offset != 0) {
            generator.put("initial-offset", offset + 1);
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
                report.add(prefix + ".group.pattern = " + pattern + ": 5.0 period keys are " + presetPattern + "; plan reformats the 4.x keys"
                    + (config.bool(prefix + ".converter.group-prefix.enabled", false)
                        ? ", and grouped string ids now carry the " + presetPattern + " prefix" : ""));
            }
            handled.add(prefix + ".group.by");
            handled.add(prefix + ".group.pattern");
            generator.put("codec", withPeriod(config, prefix + ".converter", codec));
            handled.add(prefix + ".converter.group-prefix.*");
        } else {
            generator.put("codec", codec);
        }
        return generator;
    }

    /**
     * The codec list of a periodic segment: the 4.x {@code group-prefix} becomes the {@code period} item at the place
     * the 4.x converter chain gave it (applied inside out: the base converter, the group prefix when
     * {@code before-prefix} is true, the date prefix, {@code prefix}, the group prefix when {@code before-prefix} is
     * false, {@code suffix}). Off, the period item leaves the period out of the text.
     */
    private static List<Object> withPeriod(FlatConfig config, String converter, List<Object> codec) {
        List<Object> grouped = new ArrayList<>(codec);
        Map<String, Object> period = new LinkedHashMap<>();
        if (config.bool(converter + ".group-prefix.enabled", false)) {
            period.put("delimiter", text(config, converter + ".group-prefix.delimiter", "-"));
        } else {
            period.put("enabled", false);
        }
        int at;
        if (config.bool(converter + ".group-prefix.before-prefix", true)) {
            // Right around the base converter, the only item that is no decoration.
            at = !grouped.isEmpty() && grouped.get(0) instanceof Map<?, ?> first
                && (first.containsKey("radix") || first.containsKey("friendly") || first.containsKey("custom")) ? 1 : 0;
        } else {
            // Around prefix: before the suffix, the last item when there is one.
            at = config.get(converter + ".suffix") != null ? grouped.size() - 1 : grouped.size();
        }
        grouped.add(at, Map.of("period", period));
        return grouped;
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
                    // 4.x did not pad friendly text by default, the 5.0 codec does: written either way (R2-parity-03).
                    friendly.put("pad-start", config.bool(prefix + ".friendly.pad-start", false));
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
            // 4.x prefixed the date of the conversion, not the id's.
            datePrefix.put("source", "NOW");
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
        Legacy4x legacy = new Legacy4x(config);
        Map<String, Object> generator = new LinkedHashMap<>();
        generator.put("strategy", "cosid80");
        // The shared namespace of the 4.x CosIdGenerator is the named machine of the same name (D37).
        legacy.sharedGeneratorNamespace().ifPresent(shared -> generator.put("machine", Legacy4x.GENERATOR_MACHINE));
        // The 4.x bits and threshold, as the 5.0 binding takes them over COSID_80 (D36).
        Layout layout = legacy.cosidLayout();
        if (layout.timeBits() != Layout.COSID_80.timeBits()) {
            generator.put("time-bits", layout.timeBits());
        }
        if (layout.nodeBits() != Layout.COSID_80.nodeBits()) {
            generator.put("node-bits", layout.nodeBits());
        }
        if (layout.seqBits() != Layout.COSID_80.seqBits()) {
            generator.put("seq-bits", layout.seqBits());
        }
        long derived = generator.containsKey("seq-bits") ? Layout.defaultSeqResetThreshold(layout.seqBits()) : Layout.COSID_80.seqResetThreshold();
        if (layout.seqResetThreshold() != derived) {
            generator.put("seq-reset-threshold", layout.seqResetThreshold());
        }
        if (legacy.cosidThresholdClamped()) {
            report.add("cosid.generator.sequence-reset-threshold = " + config.get("cosid.generator.sequence-reset-threshold") + " exceeds the sequence"
                + " range: written as " + layout.seqResetThreshold() + ", the largest sequence, as 4.x never resets either then");
        }
        String type = config.get("cosid.generator.type", "radix62").toLowerCase(Locale.ROOT);
        switch (type) {
            case "radix36" -> generator.put("codec", List.of(Map.of("radix", Map.of("radix", 36))));
            case "friendly" -> {
                Map<String, Object> friendly = new LinkedHashMap<>();
                friendly.put("zone", config.get("cosid.generator.zone-id", zone().getId()));
                if (!config.bool("cosid.generator.pad-start", true)) {
                    friendly.put("pad-start", false);
                }
                generator.put("codec", List.of(Map.of("friendly", friendly)));
            }
            default -> generator.put("codec", List.of(Map.of("radix", Map.of("radix", 62))));
        }
        if (!"friendly".equals(type) && !config.bool("cosid.generator.pad-start", true)) {
            report.add("cosid.generator.pad-start = false: 4.x applies it to friendly text only; the radix text of 5.0 cosid80 is padded as before");
        }
        for (String part : List.of("type", "zone-id", "timestamp-bit", "machine-bit", "sequence-bit", "sequence-reset-threshold", "pad-start", "namespace")) {
            handled.add("cosid.generator." + part);
        }
        return generator;
    }

    /**
     * The 5.0 layouts {@link #convert} gives the time-based generators of a 4.x configuration: each keeps its 4.x layout,
     * the snowflakes and, D36, the CosIdGenerator.
     *
     * @param legacy the 4.x configuration
     * @return 5.0 generator name to layout
     */
    public static Map<String, Layout> targetLayouts(Legacy4x legacy) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        legacy.snowflakes().forEach((name, prefix) -> layouts.put(name, legacy.snowflakeLayout(prefix)));
        if (legacy.cosidEnabled()) {
            // config keeps the 4.x bits of the CosIdGenerator (D36).
            layouts.put("cosid", legacy.cosidLayout());
        }
        return layouts;
    }

    private static Layout layoutOfPreset(String preset) {
        return switch (preset.toUpperCase(Locale.ROOT)) {
            case "SNOWFLAKE_S" -> Layout.SNOWFLAKE_S;
            case "JS_SAFE_MS" -> Layout.JS_SAFE_MS;
            case "JS_SAFE_S" -> Layout.JS_SAFE_S;
            default -> Layout.SNOWFLAKE_MS;
        };
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
                Layout base = Layout.COSID_80;
                int seqBits = (int) config.number(prefix + ".seq-bits", base.seqBits());
                long derived = seqBits == base.seqBits() ? base.seqResetThreshold() : Layout.defaultSeqResetThreshold(seqBits);
                layouts.put(name, new Layout(base.epoch(), base.unit(), (int) config.number(prefix + ".time-bits", base.timeBits()),
                    (int) config.number(prefix + ".node-bits", base.nodeBits()), seqBits, config.number(prefix + ".seq-reset-threshold", derived)));
            } else if ("snowflake".equals(strategy)) {
                Layout base = layoutOfPreset(config.get(prefix + ".layout", "SNOWFLAKE_MS"));
                String epoch = config.get(prefix + ".epoch");
                String unit = config.get(prefix + ".unit");
                int seqBits = (int) config.number(prefix + ".seq-bits", base.seqBits());
                long derived = seqBits == base.seqBits() ? base.seqResetThreshold() : Layout.defaultSeqResetThreshold(seqBits);
                layouts.put(name, new Layout(epoch == null ? base.epoch() : Instant.parse(epoch),
                    unit == null ? base.unit() : ChronoUnit.valueOf(unit.toUpperCase(Locale.ROOT)),
                    (int) config.number(prefix + ".time-bits", base.timeBits()), (int) config.number(prefix + ".node-bits", base.nodeBits()), seqBits,
                    config.number(prefix + ".seq-reset-threshold", derived)));
            }
        }
        return layouts;
    }
}
