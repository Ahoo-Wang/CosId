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
import me.ahoo.cosid.store.StoreKeys;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.AbstractBindHandler;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@link CosIdProperties} to {@link RuntimeSpec} (07-runtime-and-spring.md §4.1): {@link #toSpec} is a pure function,
 * no Spring involved; {@link #bind} binds {@code cosid.*} strictly. It checks what the properties alone can tell (a missing strategy, a codec item naming two codecs, ...) and reports
 * every problem at once by property path; {@code CosIdRuntime.start} validates the spec as a whole.
 */
public final class CosIdSpecs {
    public static final String SNOWFLAKE = "snowflake";
    public static final String COSID80 = "cosid80";
    public static final String SEGMENT = "segment";
    public static final String PERIODIC_SEGMENT = "periodic-segment";
    /** The strategy of a user generator ({@code GeneratorSpec.Custom} or {@code Instance}) in {@code describe()}. */
    public static final String CUSTOM = "custom";
    /** UUIDv7 ids (D46): no machine, store nor codec. */
    public static final String UUIDV7 = "uuidv7";

    private static final Map<String, Layout> LAYOUTS = Map.of(
        "SNOWFLAKE_MS", Layout.SNOWFLAKE_MS,
        "SNOWFLAKE_S", Layout.SNOWFLAKE_S,
        "JS_SAFE_MS", Layout.JS_SAFE_MS,
        "JS_SAFE_S", Layout.JS_SAFE_S);
    /** The {@code cosid.*} keys bound elsewhere: the integrations and the server. */
    private static final List<String> FOREIGN_KEYS = List.of("server", "mybatis", "jdbc", "activiti", "flowable", "axon");
    private static final Map<String, Period> PERIODS = Map.of(
        "YEAR", Period.YEAR,
        "YEAR_MONTH", Period.YEAR_MONTH,
        "YEAR_MONTH_DAY", Period.YEAR_MONTH_DAY);

    private static final Logger log = LoggerFactory.getLogger(CosIdSpecs.class);

    private CosIdSpecs() {
    }

    /**
     * Binds {@code cosid.*}; a key that binds to nothing fails (R1-spring-03): a typo would otherwise fall back to the
     * default silently. {@code cosid.stores.*.options}, the keys of the integrations and the server, and system
     * properties and environment variables are not checked here.
     *
     * @param sources      the property sources, such as {@code ConfigurationPropertySources.get(environment)}
     * @param placeholders resolves {@code ${...}} in the values
     * @return the properties
     * @throws org.springframework.boot.context.properties.bind.BindException with the unbound keys
     */
    public static CosIdProperties bind(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholders) {
        return new Binder(sources, placeholders, EmptyItems.CONVERSION).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class), new UnknownKeys(sources));
    }

    /**
     * An empty value of a {@link CosIdProperties} record, as YAML {@code - friendly: {}} gives, is that record with its
     * defaults (R1-spring-10). Spring Boot 4.1 binds it so by itself; 4.0 tries to convert the empty text and fails.
     */
    static final class EmptyItems implements ConditionalGenericConverter {
        static final ApplicationConversionService CONVERSION = new ApplicationConversionService();

        static {
            CONVERSION.addConverter(new EmptyItems());
        }

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            return Set.of(new ConvertiblePair(String.class, Record.class));
        }

        @Override
        public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
            return targetType.getType().getEnclosingClass() == CosIdProperties.class;
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null;
            }
            if (!source.toString().isBlank()) {
                throw new IllegalArgumentException("Not a " + targetType.getType().getSimpleName() + ": " + source);
            }
            return new Binder(new MapConfigurationPropertySource()).bindOrCreate("empty", Bindable.of(targetType.getType()));
        }
    }

    /**
     * Fails the binding when a {@code cosid.*} key bound to nothing; the keys checked elsewhere and empty values, such as
     * {@code preferred-slot: ${POD_ORDINAL:}}, which bind to null, are not unknown. Nor are the keys of a list in a
     * source below the one it bound from (R2-spring-01): Spring binds a list, such as a {@code codec}, from the first
     * source that has it, as a whole, so a profile or the command line replaces it.
     */
    private static final class UnknownKeys extends AbstractBindHandler {
        private final Iterable<ConfigurationPropertySource> sources;
        private final Set<ConfigurationPropertyName> bound = new HashSet<>();
        private final Set<ConfigurationPropertyName> lists = new HashSet<>();

        private UnknownKeys(Iterable<ConfigurationPropertySource> sources) {
            this.sources = sources;
        }

        @Override
        public Object onSuccess(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) {
            bound.add(name);
            if (result instanceof Collection<?>) {
                lists.add(name);
            }
            return super.onSuccess(name, target, context, result);
        }

        @Override
        public void onFinish(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) throws Exception {
            if (context.getDepth() == 0) {
                Set<String> unknown = new TreeSet<>();
                Set<String> bracketed = new TreeSet<>();
                // Each bound list and the source it bound from: the first, in order, that has a key of it.
                Map<ConfigurationPropertyName, ConfigurationPropertySource> listSources = new HashMap<>();
                for (ConfigurationPropertySource source : sources) {
                    if (source instanceof IterableConfigurationPropertySource iterable) {
                        boolean system = isSystem(source);
                        iterable.filter(name::isAncestorOf).forEach(key -> {
                            Optional<ConfigurationPropertyName> list = lists.stream().filter(root -> root.isAncestorOf(key)).findFirst();
                            if (list.isPresent() && listSources.computeIfAbsent(list.get(), root -> source) != source) {
                                return;
                            }
                            if (!system && !bound.contains(key) && !boundElsewhere(key) && !isEmpty(source, key)) {
                                unknown.add(key.toString());
                                bracketed(key).ifPresent(bracketed::add);
                            }
                        });
                    }
                }
                if (!unknown.isEmpty()) {
                    throw new CosIdException("Unknown " + CosIdProperties.PREFIX + ".* keys " + unknown + ": they bind to nothing; check their spelling"
                        + (bracketed.isEmpty() ? "" : "; a name with a dot goes in brackets, such as " + bracketed
                            + " (in YAML the key \"[name]\")"));
                }
            }
            super.onFinish(name, target, context, result);
        }
    }

    /** The entry type of each map of named entries under {@code cosid.*}, whose names may contain a dot (StoreKeys). */
    private static final Map<String, Class<? extends Record>> NAMED_ENTRIES = Map.of(
        "generators", CosIdProperties.Generator.class,
        "machines", CosIdProperties.NamedMachine.class,
        "stores", CosIdProperties.Store.class);

    /**
     * An unknown key under a map of named entries as it reads with a dotted name in brackets:
     * {@code cosid.generators.eshop-ordering.order.strategy} is {@code cosid.generators[eshop-ordering.order].strategy}.
     * The name ends where a property of the entry type begins; empty when none does, or when the name is one element
     * (a typo after an ordinary name, such as {@code cosid.generators.order.codec[0].radx}, needs no brackets).
     */
    static Optional<String> bracketed(ConfigurationPropertyName key) {
        int elements = key.getNumberOfElements();
        Class<? extends Record> entry = elements > 4 ? NAMED_ENTRIES.get(key.getElement(1, ConfigurationPropertyName.Form.DASHED)) : null;
        if (entry == null) {
            return Optional.empty();
        }
        Set<String> properties = properties(entry);
        if (properties.contains(key.getElement(3, ConfigurationPropertyName.Form.DASHED))) {
            return Optional.empty();
        }
        for (int at = 4; at < elements; at++) {
            if (!key.isNumericIndex(at) && properties.contains(key.getElement(at, ConfigurationPropertyName.Form.DASHED))) {
                StringBuilder text = new StringBuilder(CosIdProperties.PREFIX).append('.').append(key.getElement(1, ConfigurationPropertyName.Form.DASHED))
                    .append('[');
                for (int part = 2; part < at; part++) {
                    text.append(part > 2 ? "." : "").append(key.getElement(part, ConfigurationPropertyName.Form.ORIGINAL));
                }
                text.append(']');
                for (int part = at; part < elements; part++) {
                    String element = key.getElement(part, ConfigurationPropertyName.Form.ORIGINAL);
                    text.append(key.isNumericIndex(part) ? "[" + element + "]" : "." + element);
                }
                return Optional.of(text.toString());
            }
        }
        return Optional.empty();
    }

    /** The property names of a record bound by its constructor: each component's, dashed, or its {@link Name}. */
    private static Set<String> properties(Class<? extends Record> type) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        for (int at = 0; at < components.length; at++) {
            types[at] = components[at].getType();
        }
        Parameter[] parameters;
        try {
            parameters = type.getDeclaredConstructor(types).getParameters();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("A record without its canonical constructor: " + type, e);
        }
        Set<String> properties = new HashSet<>();
        for (int at = 0; at < components.length; at++) {
            Name renamed = parameters[at].getAnnotation(Name.class);
            properties.add(renamed != null ? renamed.value() : components[at].getName().replaceAll("([A-Z])", "-$1").toLowerCase(Locale.ROOT));
        }
        return properties;
    }

    /**
     * System properties and environment variables are shared with everything else in the process ({@code COSID_TEST_*},
     * a test's {@code -Dcosid.*}): only configuration files and command line arguments are checked.
     */
    private static boolean isSystem(ConfigurationPropertySource source) {
        return source.getUnderlyingSource() instanceof PropertySource<?> underlying
            && (StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(underlying.getName())
                || StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(underlying.getName()));
    }

    private static boolean isEmpty(ConfigurationPropertySource source, ConfigurationPropertyName name) {
        ConfigurationProperty property = source.getConfigurationProperty(name);
        return property != null && property.getValue() instanceof CharSequence value && value.isEmpty();
    }

    private static boolean boundElsewhere(ConfigurationPropertyName name) {
        if (name.getNumberOfElements() < 2 || !CosIdProperties.PREFIX.equals(name.getElement(0, ConfigurationPropertyName.Form.UNIFORM))) {
            return true;
        }
        String second = name.getElement(1, ConfigurationPropertyName.Form.DASHED);
        if (FOREIGN_KEYS.contains(second)) {
            return true;
        }
        return "stores".equals(second) && name.getNumberOfElements() > 3 && "options".equals(name.getElement(3, ConfigurationPropertyName.Form.DASHED));
    }

    /**
     * The namespace of the runtime (D38): {@code cosid.namespace}, else {@code spring.application.name}, else
     * {@value CosIdProperties#DEFAULT_NAMESPACE}.
     *
     * @param configured      {@code cosid.namespace}, or null
     * @param applicationName {@code spring.application.name}, or null
     * @return the namespace; empty when both are unset, for {@value CosIdProperties#DEFAULT_NAMESPACE}
     * @throws CosIdException if the application name is no valid namespace
     */
    public static Optional<String> namespace(String configured, String applicationName) {
        if (configured != null) {
            return Optional.of(configured);
        }
        if (applicationName == null || applicationName.isBlank()) {
            return Optional.empty();
        }
        if (!StoreKeys.isNamespace(applicationName)) {
            throw new CosIdException("spring.application.name [" + applicationName + "] is no valid namespace (1 to 100 letters, digits, '.', '_', ':' "
                + "or '-', starting with a letter or digit): set " + CosIdProperties.PREFIX + ".namespace");
        }
        return Optional.of(applicationName);
    }

    /**
     * Maps the properties to a spec, in {@code cosid.namespace} or {@value CosIdProperties#DEFAULT_NAMESPACE}.
     *
     * @param properties the bound properties
     * @param zone       the zone of the zone-dependent settings left empty (the system zone in an application)
     * @return the spec
     * @throws CosIdException listing every problem by property path
     */
    public static RuntimeSpec toSpec(CosIdProperties properties, ZoneId zone) {
        return toSpec(properties, properties.namespace() == null ? CosIdProperties.DEFAULT_NAMESPACE : properties.namespace(), zone);
    }

    /**
     * Maps the properties to a spec.
     *
     * @param properties the bound properties
     * @param namespace  the namespace of the runtime, resolved by {@link #namespace(String, String)}
     * @param zone       the zone of the zone-dependent settings left empty (the system zone in an application)
     * @return the spec
     * @throws CosIdException listing every problem by property path
     */
    public static RuntimeSpec toSpec(CosIdProperties properties, String namespace, ZoneId zone) {
        List<String> problems = new ArrayList<>();
        Map<String, StoreSpec> stores = new LinkedHashMap<>();
        new TreeMap<>(properties.stores()).forEach((name, store) -> {
            String at = CosIdProperties.PREFIX + ".stores." + name;
            if (store.type() == null || store.type().isBlank()) {
                problems.add(at + ".type: required");
                return;
            }
            stores.put(name, StoreSpec.builder(store.type().trim()).timeout(store.timeout()).options(store.options()).build());
        });
        String machineAt = CosIdProperties.PREFIX + ".machine";
        final Optional<MachineSpec> machine = properties.machine() == null ? Optional.empty()
            : machine(machineAt, properties.machine().namespace(), properties.machine().leased(), properties.machine().fixed(), problems);
        Map<String, MachineSpec> machines = new LinkedHashMap<>();
        new TreeMap<>(properties.machines()).forEach((name, named) -> {
            String at = CosIdProperties.PREFIX + ".machines." + name;
            if (named.namespace() == null || named.namespace().isBlank()) {
                // Without one the machine would share the namespace of the default machine (api-freeze.md §2.2).
                problems.add(at + ".namespace: required: a named machine has a namespace of its own");
            }
            if (named.leased() == null && named.fixed() == null) {
                problems.add(at + ": set leased or static");
            }
            machine(at, named.namespace(), named.leased(), named.fixed(), problems).ifPresent(spec -> machines.put(name, spec));
        });
        Map<String, GeneratorSpec> generators = new LinkedHashMap<>();
        new TreeMap<>(properties.generators()).forEach((name, generator) -> {
            GeneratorSpec spec = generator(CosIdProperties.PREFIX + ".generators." + name, generator, properties.generators().keySet(), zone, problems);
            if (spec != null) {
                generators.put(name, spec);
            }
        });
        if (namespace == null || namespace.isBlank()) {
            problems.add(CosIdProperties.PREFIX + ".namespace: must not be blank");
        }
        if (!problems.isEmpty()) {
            throw new CosIdException("Invalid " + CosIdProperties.PREFIX + ".* configuration:\n  - " + String.join("\n  - ", problems));
        }
        RuntimeSpec.Builder spec = RuntimeSpec.builder(namespace);
        stores.forEach(spec::store);
        machine.ifPresent(spec::machine);
        machines.forEach(spec::machine);
        generators.forEach(spec::generator);
        return spec.build();
    }

    private static Optional<MachineSpec> machine(String at, String namespace, CosIdProperties.Leased leased, CosIdProperties.Static fixed,
                                                 List<String> problems) {
        if (leased == null && fixed == null) {
            if (namespace != null && at.endsWith(".machine")) {
                problems.add(at + ".namespace: set with leased or static");
            }
            return Optional.empty();
        }
        if (leased != null && fixed != null) {
            problems.add(at + ": set either leased or static, not both");
            return Optional.empty();
        }
        if (leased != null) {
            if (leased.store() == null || leased.store().isBlank()) {
                problems.add(at + ".leased.store: required");
                return Optional.empty();
            }
            MachineSpec.Leased.Builder spec = MachineSpec.Leased.builder(leased.store()).bits(leased.bits()).ttl(leased.ttl()).tolerance(leased.tolerance())
                .slotOffset(leased.slotOffset());
            // Unset follows the ttl, not a fixed default: a changed ttl moves it along (api-freeze.md §6).
            Optional.ofNullable(leased.acquireTimeout()).ifPresent(spec::acquireTimeout);
            if (leased.preferredSlot() != null) {
                spec.preferredSlot(leased.preferredSlot());
            }
            Optional.ofNullable(leased.ownerKey()).filter(key -> !key.isEmpty()).ifPresent(spec::ownerKey);
            Optional.ofNullable(namespace).filter(ns -> !ns.isBlank()).ifPresent(spec::namespace);
            return Optional.of(spec.build());
        }
        if (fixed.slot() == null) {
            problems.add(at + ".static.slot: required");
            return Optional.empty();
        }
        MachineSpec.Static.Builder spec = MachineSpec.Static.builder(fixed.slot()).bits(fixed.bits()).ahead(fixed.ahead());
        Optional.ofNullable(fixed.stateFile()).ifPresent(spec::stateFile);
        Optional.ofNullable(fixed.notBefore()).ifPresent(spec::notBefore);
        Optional.ofNullable(namespace).filter(ns -> !ns.isBlank()).ifPresent(spec::namespace);
        return Optional.of(spec.build());
    }

    private static GeneratorSpec generator(String at, CosIdProperties.Generator generator, Set<String> names, ZoneId zone, List<String> problems) {
        String strategy = generator.strategy() == null ? "" : generator.strategy().trim().toLowerCase(Locale.ROOT);
        if (!List.of(SNOWFLAKE, COSID80, SEGMENT, PERIODIC_SEGMENT, CUSTOM, UUIDV7).contains(strategy)) {
            problems.add(at + ".strategy: one of snowflake, cosid80, segment, periodic-segment, custom, uuidv7, not [" + generator.strategy() + "]");
            return null;
        }
        // A disabled generator is not validated (D24): only its strategy, which gives its bean type, must be known.
        List<String> own = generator.disabled() ? new ArrayList<>() : problems;
        boolean timeBased = SNOWFLAKE.equals(strategy) || COSID80.equals(strategy);
        if (generator.machine() != null && !timeBased) {
            own.add(at + ".machine: only a snowflake or cosid80 generator chooses a machine");
        }
        if (generator.ref() != null && !CUSTOM.equals(strategy)) {
            own.add(at + ".ref: only a custom generator refers to a bean");
        }
        GeneratorSpec spec;
        try {
            spec = switch (strategy) {
                case SNOWFLAKE -> {
                    GeneratorSpec.Snowflake.Builder snowflake = GeneratorSpec.Snowflake.builder()
                        .layout(layout(at, generator, own))
                        .regression(regression(generator))
                        .codec(codecs(at, generator.codec(), zone, own));
                    Optional.ofNullable(generator.machine()).ifPresent(snowflake::machine);
                    yield snowflake.build();
                }
                case COSID80 -> {
                    GeneratorSpec.CosId80.Builder cosId80 = GeneratorSpec.CosId80.builder()
                        .layout(cosId80Layout(at, generator, own))
                        .regression(regression(generator))
                        .codec(partsCodec(at, generator.codec(), zone, own));
                    Optional.ofNullable(generator.machine()).ifPresent(cosId80::machine);
                    yield cosId80.build();
                }
                case CUSTOM -> GeneratorSpec.Custom.builder(ref(at, generator, names, own))
                    .codec(codecs(at, generator.codec(), zone, own))
                    .build();
                case UUIDV7 -> {
                    uuidV7Keys(generator).forEach(key -> own.add(at + "." + key + ": a uuidv7 generator has no " + key
                        + " (it needs no machine nor store, and its text is the canonical UUID form)"));
                    yield GeneratorSpec.UuidV7.builder().build();
                }
                case SEGMENT -> GeneratorSpec.Segment.builder(store(at, generator, own))
                    .batch(batch(generator))
                    .codec(codecs(at, generator.codec(), zone, own))
                    .build();
                default -> GeneratorSpec.PeriodicSegment.builder(store(at, generator, own), period(at, generator, own), generator.zone() == null ? zone : generator.zone())
                    .batch(batch(generator))
                    .periodic(periodic(generator))
                    .codec(groupedCodec(at, generator.codec(), zone, own))
                    .build();
            };
        } catch (IllegalArgumentException e) {
            own.add(at + ": " + e.getMessage());
            spec = null;
        }
        if (!generator.disabled()) {
            List<String> ignored = ignoredKeys(strategy, generator);
            if (!ignored.isEmpty()) {
                // R2-spring-02 (user's decision): a warning, not a failed start, which would tighten cosid.* after RC1.
                log.warn("{}: {} do not apply to a {} generator and are ignored", at, ignored, strategy);
            }
            return spec;
        }
        return new GeneratorSpec.Disabled(spec != null && own.isEmpty() ? spec : placeholder(strategy, generator));
    }

    /** The strategies each key without a default applies to; cosid80 and uuidv7 refuse the others themselves. */
    private static final Map<String, Set<String>> KEY_STRATEGIES = Map.ofEntries(
        Map.entry("layout", Set.of(SNOWFLAKE)),
        Map.entry("epoch", Set.of(SNOWFLAKE)),
        Map.entry("unit", Set.of(SNOWFLAKE)),
        Map.entry("time-bits", Set.of(SNOWFLAKE, COSID80)),
        Map.entry("node-bits", Set.of(SNOWFLAKE, COSID80)),
        Map.entry("seq-bits", Set.of(SNOWFLAKE, COSID80)),
        Map.entry("seq-reset-threshold", Set.of(SNOWFLAKE, COSID80)),
        Map.entry("store", Set.of(SEGMENT, PERIODIC_SEGMENT)),
        Map.entry("max-batch", Set.of(SEGMENT, PERIODIC_SEGMENT)),
        Map.entry("range-ttl", Set.of(SEGMENT, PERIODIC_SEGMENT)),
        Map.entry("period", Set.of(PERIODIC_SEGMENT)),
        Map.entry("zone", Set.of(PERIODIC_SEGMENT)),
        Map.entry("seal-after", Set.of(PERIODIC_SEGMENT)));

    /**
     * The keys set on a snowflake, segment, periodic segment or custom generator that its strategy has no use for, such
     * as {@code period} on a segment (R2-spring-02): ignored, with a warning. Keys with a default value cannot tell a
     * setting from the default and are not reported, as for {@link #uuidV7Keys}.
     */
    static List<String> ignoredKeys(String strategy, CosIdProperties.Generator generator) {
        if (COSID80.equals(strategy) || UUIDV7.equals(strategy)) {
            return List.of();
        }
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("layout", generator.layout());
        keys.put("epoch", generator.epoch());
        keys.put("unit", generator.unit());
        keys.put("time-bits", generator.timeBits());
        keys.put("node-bits", generator.nodeBits());
        keys.put("seq-bits", generator.seqBits());
        keys.put("seq-reset-threshold", generator.seqResetThreshold());
        keys.put("store", generator.store());
        keys.put("max-batch", generator.maxBatch());
        keys.put("range-ttl", generator.rangeTtl());
        keys.put("period", generator.period());
        keys.put("zone", generator.zone());
        keys.put("seal-after", generator.sealAfter());
        List<String> ignored = new ArrayList<>();
        keys.forEach((key, value) -> {
            if (value != null && !KEY_STRATEGIES.get(key).contains(strategy)) {
                ignored.add(key);
            }
        });
        return ignored;
    }

    /**
     * The keys set on a uuidv7 generator that only other strategies have (D46); the machine and the ref are checked for
     * every strategy. Keys with a default value cannot tell a setting from the default and are not reported.
     */
    private static List<String> uuidV7Keys(CosIdProperties.Generator generator) {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("layout", generator.layout());
        keys.put("epoch", generator.epoch());
        keys.put("unit", generator.unit());
        keys.put("time-bits", generator.timeBits());
        keys.put("node-bits", generator.nodeBits());
        keys.put("seq-bits", generator.seqBits());
        keys.put("seq-reset-threshold", generator.seqResetThreshold());
        keys.put("store", generator.store());
        keys.put("max-batch", generator.maxBatch());
        keys.put("range-ttl", generator.rangeTtl());
        keys.put("period", generator.period());
        keys.put("zone", generator.zone());
        keys.put("seal-after", generator.sealAfter());
        keys.put("codec", generator.codec().isEmpty() ? null : generator.codec());
        List<String> set = new ArrayList<>();
        keys.forEach((key, value) -> {
            if (value != null) {
                set.add(key);
            }
        });
        return set;
    }

    /** What a disabled generator that would not map stands for: only its strategy matters. */
    private static GeneratorSpec placeholder(String strategy, CosIdProperties.Generator generator) {
        String store = generator.store() == null ? "" : generator.store();
        return switch (strategy) {
            case SNOWFLAKE -> GeneratorSpec.Snowflake.builder()
                .layout(Layout.SNOWFLAKE_MS)
                .regression(ClockRegressionPolicy.DEFAULT)
                .codec(List.of())
                .build();
            case COSID80 -> GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Radix(62)).build();
            case CUSTOM -> GeneratorSpec.Custom.builder(generator.ref() == null ? "" : generator.ref()).build();
            case UUIDV7 -> GeneratorSpec.UuidV7.builder().build();
            case SEGMENT -> GeneratorSpec.Segment.builder(store).batch(BatchPolicy.DEFAULT).codec(List.of()).build();
            default -> GeneratorSpec.PeriodicSegment.builder(store, Period.YEAR_MONTH_DAY, ZoneId.of("UTC"))
                .batch(BatchPolicy.DEFAULT)
                .periodic(PeriodicPolicy.DEFAULT)
                .codec(new GroupedCodecSpec.DatePrefix("-", List.of()))
                .build();
        };
    }

    /** The bean a custom generator refers to: required, and not one of the generators, whose beans CosId registers. */
    private static String ref(String at, CosIdProperties.Generator generator, Set<String> names, List<String> problems) {
        if (generator.ref() == null || generator.ref().isBlank()) {
            problems.add(at + ".ref: required: the bean name of an IdGenerator");
            return "";
        }
        if (names.contains(generator.ref())) {
            problems.add(at + ".ref: [" + generator.ref() + "] is the name of a generator, whose bean CosId registers: refer to another bean");
        }
        return generator.ref();
    }

    private static Layout layout(String at, CosIdProperties.Generator generator, List<String> problems) {
        Layout preset = LAYOUTS.get(generator.layout() == null ? "SNOWFLAKE_MS" : generator.layout().trim().toUpperCase(Locale.ROOT));
        if (preset == null) {
            problems.add(at + ".layout: one of " + new TreeMap<>(LAYOUTS).keySet() + ", not [" + generator.layout() + "]");
            return Layout.SNOWFLAKE_MS;
        }
        return override(preset, generator);
    }

    /**
     * The layout of a cosid80 generator (D36): {@code COSID_80} with its bit widths and reset threshold overridable; the
     * Unix epoch and milliseconds stay, so the 80-bit text decodes alike everywhere.
     */
    private static Layout cosId80Layout(String at, CosIdProperties.Generator generator, List<String> problems) {
        if (generator.layout() != null && !"COSID_80".equals(generator.layout().trim().toUpperCase(Locale.ROOT))) {
            problems.add(at + ".layout: a cosid80 generator takes COSID_80 only, not [" + generator.layout() + "]");
        }
        if (generator.epoch() != null) {
            problems.add(at + ".epoch: a cosid80 generator keeps the Unix epoch");
        }
        if (generator.unit() != null) {
            problems.add(at + ".unit: a cosid80 generator keeps milliseconds");
        }
        // A set epoch or unit is reported above; the spec is not built then.
        return override(Layout.COSID_80, generator);
    }

    /** The preset with the epoch, unit, bit widths and reset threshold the generator sets. */
    private static Layout override(Layout preset, CosIdProperties.Generator generator) {
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
        BatchPolicy.Builder batch = BatchPolicy.builder()
            .step(generator.step())
            .initialOffset(generator.initialOffset())
            .lookahead(generator.lookahead())
            .maxBatch(maxBatch)
            .reserveTimeout(generator.reserveTimeout());
        Optional.ofNullable(generator.rangeTtl()).ifPresent(batch::rangeTtl);
        return batch.build();
    }

    private static PeriodicPolicy periodic(CosIdProperties.Generator generator) {
        PeriodicPolicy.Builder periodic = PeriodicPolicy.builder().prewarm(generator.prewarm());
        Optional.ofNullable(generator.sealAfter()).ifPresent(periodic::sealAfter);
        return periodic.build();
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
            return new CodecSpec.Radix(radix.radix(), radix.width() == null ? Radix.maxWidth(radix.radix()) : radix.width(), Boolean.TRUE.equals(radix.padStart()));
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
        if (set == 0) {
            // R1-spring-10: an item of its own with nothing in it ("- {}"); "- friendly: {}" is a friendly item with defaults.
            problems.add(at + ": an empty item: write one of radix, friendly, prefix, suffix, date-prefix, custom, period, such as"
                + " \"- friendly: {}\" (its defaults) or \"- radix: { radix: 62 }\"");
            return false;
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
            CosIdProperties.Radix radix = item.radix();
            // The layout gives each part of the 80-bit text its width (D36).
            if (radix.width() != null) {
                problems.add(first + ".radix.width: a cosid80 generator's layout gives each part its width");
            }
            if (radix.padStart() != null) {
                problems.add(first + ".radix.pad-start: a cosid80 generator's radix text is always padded to the widths of its layout");
            }
            if (radix.radix() != 62 && radix.radix() != 36) {
                problems.add(first + ".radix.radix: a cosid80 generator takes 62 or 36: " + radix.radix());
            }
            return new PartsCodecSpec.Radix(radix.radix());
        }
        return new PartsCodecSpec.Friendly(item.friendly().zone() == null ? zone : item.friendly().zone(), item.friendly().padStart());
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
