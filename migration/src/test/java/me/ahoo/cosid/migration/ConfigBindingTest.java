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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.migration.config.ConfigConverter;
import me.ahoo.cosid.migration.config.FlatConfig;
import me.ahoo.cosid.migration.config.Legacy4x;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.spring.autoconfigure.CosIdProperties;
import me.ahoo.cosid.spring.autoconfigure.CosIdSpecs;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.remote.RemoteStoreProvider;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.store.tck.ManualClock;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@code config} golden binds as a 5.0 application binds it: Spring Boot's YAML loader and {@link Binder} into
 * R3-S's {@link CosIdProperties}, then {@link CosIdSpecs#toSpec} into a {@link RuntimeSpec}.
 */
class ConfigBindingTest {
    @ParameterizedTest
    @ValueSource(strings = {"yaml", "properties", "segments-only", "edge"})
    void theGoldenBindsToARuntimeSpec(String golden) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(golden,
            new FileSystemResource(MigrationScenarioTest.resource("config/" + golden + "/expected.yaml")))) {
            environment.getPropertySources().addFirst(source);
        }
        // The placeholders config writes for slots it cannot know.
        environment.getPropertySources().addLast(new MapPropertySource("slots", Map.of("POD_ORDINAL", "3", "COSID_SLOT", "5")));
        CosIdProperties properties = Binder.get(environment).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class));
        RuntimeSpec spec = CosIdSpecs.toSpec(properties, ZoneId.of("UTC"));
        assertThat(spec.namespace(), is(properties.namespace()));
        assertThat(spec.generators().keySet(), is(properties.generators().keySet()));
        // Every store opens through its real provider config; only the remote one opens without a server behind it.
        spec.stores().forEach((name, store) -> {
            if (RemoteStoreProvider.TYPE.equals(store.type())) {
                try (Store remote = new RemoteStoreProvider().open(new StoreConfig(name, store.type(), store.timeout(), store.options(), Optional.empty()))) {
                    assertThat(remote.ranges(), notNullValue());
                } catch (Exception e) {
                    throw new AssertionError("store [" + name + "] does not open: " + e, e);
                }
            }
        });
    }

    @Test
    void theGroupPrefixFormsOf4xBindToTheirPeriodItems() throws IOException {
        RuntimeSpec spec = edgeSpec();
        // before-prefix = false: the period around the business prefix, inside the suffix.
        assertThat(codecOf(spec, "grouped"), is(new GroupedCodecSpec.DatePrefix("-",
            List.of(new CodecSpec.Radix(62, 11, true), new CodecSpec.Prefix("G-")), List.of(new CodecSpec.Suffix("!")))));
        // before-prefix = true (the default): the period right around the value, inside the date prefix and prefix.
        assertThat(codecOf(spec, "grouped-inside"), is(new GroupedCodecSpec.DatePrefix("/", List.of(new CodecSpec.Radix(36, 13, true)),
            List.of(new CodecSpec.DatePrefix("yy", "-", DatePrefix.Source.NOW, ZoneId.of("UTC")), new CodecSpec.Prefix("P")))));
        // enabled = false: the value alone.
        assertThat(codecOf(spec, "grouped-plain"), is(new GroupedCodecSpec.ValueOnly(List.of(new CodecSpec.Prefix("N")))));
    }

    @Test
    void theSequenceResetThresholdOf4xKeepsItsLayout() throws IOException {
        // cosid.snowflake.share.sequence-reset-threshold = 7 in the edge input: converted, and bound to the same layout.
        Legacy4x legacy = new Legacy4x(FlatConfig.load(MigrationScenarioTest.resource("config/edge/input.yaml")));
        Layout layout = ConfigConverter.targetLayouts(legacy).get(Legacy4x.DEFAULT);
        assertThat(layout.seqResetThreshold(), is(7L));
        assertThat(((GeneratorSpec.Snowflake) edgeSpec().generators().get(Legacy4x.DEFAULT)).layout(), is(layout));
        assertThat(ConfigConverter.layoutsOf5(FlatConfig.load(MigrationScenarioTest.resource("config/edge/expected.yaml"))).get(Legacy4x.DEFAULT),
            is(layout));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aThresholdAloneKeepsThePreset(@TempDir Path dir) throws IOException {
        // R1-x-04: a 4.x preset layout with its own sequence-reset-threshold is the preset plus seq-reset-threshold.
        Path input = dir.resolve("application.yaml");
        Files.writeString(input, """
            cosid:
              namespace: scatter
              snowflake:
                enabled: true
                share: { sequence-reset-threshold: 100 }
                provider:
                  seconds: { timestamp-unit: second, timestamp-bit: 31, sequence-bit: 22, sequence-reset-threshold: 5 }
                  js-ms: { machine-bit: 3, sequence-bit: 9, sequence-reset-threshold: 5 }
                  js-s: { timestamp-unit: second, timestamp-bit: 31, machine-bit: 3, sequence-bit: 19, sequence-reset-threshold: 5 }
              generator: { enabled: true, sequence-reset-threshold: 7 }
            """);
        Legacy4x legacy = new Legacy4x(FlatConfig.load(input));
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("UTC")).convert(legacy.config(), null);
        Map<String, Object> cosid = (Map<String, Object>) conversion.model().get("cosid");
        Map<String, Object> generator = (Map<String, Object>) ((Map<String, Object>) cosid.get("generators")).get(Legacy4x.DEFAULT);
        assertThat(generator.get("layout"), is("SNOWFLAKE_MS"));
        assertThat(generator.get("seq-reset-threshold"), is(100L));
        assertThat(conversion.report(), not(hasItem(containsString("no 5.0 preset"))));
        // The cosid80 threshold cannot be set in 5.0 yet: reported, not taken for other bits.
        assertThat(conversion.report(), hasItem(containsString("cosid.generator.sequence-reset-threshold = 7")));
        assertThat(conversion.report(), not(hasItem(containsString("the 4.x bits differ"))));
        Path output = dir.resolve("application-5.yaml");
        conversion.write(output);
        Layout layout = legacy.snowflakeLayout("cosid.snowflake.share");
        assertThat(layout.seqResetThreshold(), is(100L));
        RuntimeSpec spec = specOf(output);
        assertThat(((GeneratorSpec.Snowflake) spec.generators().get(Legacy4x.DEFAULT)).layout(), is(layout));
        Map<String, Object> generators = (Map<String, Object>) cosid.get("generators");
        Map<String, String> presets = Map.of("seconds", "SNOWFLAKE_S", "js-ms", "JS_SAFE_MS", "js-s", "JS_SAFE_S");
        presets.forEach((name, preset) -> {
            Map<String, Object> provider = (Map<String, Object>) generators.get(name);
            assertThat(name, provider.get("layout"), is(preset));
            assertThat(name, provider.get("seq-reset-threshold"), is(5L));
            assertThat(name, ((GeneratorSpec.Snowflake) spec.generators().get(name)).layout(),
                is(legacy.snowflakeLayout("cosid.snowflake.provider." + name)));
        });
    }

    @Test
    void theStatefulSetSlotBindsFromThePodOrdinal() throws IOException {
        MachineSpec.Static machine = (MachineSpec.Static) edgeSpec().machine().orElseThrow();
        assertThat(machine.slot(), is(3));
        assertThat(machine.bits(), is(8));
    }

    private static GroupedCodecSpec codecOf(RuntimeSpec spec, String generator) {
        return ((GeneratorSpec.PeriodicSegment) spec.generators().get(generator)).codec();
    }

    private static RuntimeSpec edgeSpec() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("edge",
            new FileSystemResource(MigrationScenarioTest.resource("config/edge/expected.yaml")))) {
            environment.getPropertySources().addFirst(source);
        }
        environment.getPropertySources().addLast(new MapPropertySource("slots", Map.of("POD_ORDINAL", "3")));
        return CosIdSpecs.toSpec(Binder.get(environment).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class)), ZoneId.of("UTC"));
    }

    /** Binds a golden as {@link #theGoldenBindsToARuntimeSpec} does. */
    static RuntimeSpec spec(String golden) throws IOException {
        return specOf(MigrationScenarioTest.resource("config/" + golden + "/expected.yaml"));
    }

    private static RuntimeSpec specOf(Path yaml) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(yaml.getFileName().toString(), new FileSystemResource(yaml))) {
            environment.getPropertySources().addFirst(source);
        }
        environment.getPropertySources().addLast(new MapPropertySource("slots", Map.of("POD_ORDINAL", "3", "COSID_SLOT", "5")));
        return CosIdSpecs.toSpec(Binder.get(environment).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class)), ZoneId.of("UTC"));
    }

    @Test
    void aNonZero4xOffsetStartsNewKeysAtOffsetPlusOne() throws IOException {
        RuntimeSpec spec = spec("yaml");
        assertThat(((GeneratorSpec.Segment) spec.generators().get("order")).batch().initialOffset(), is(10_001L));
        GeneratorSpec.PeriodicSegment bill = (GeneratorSpec.PeriodicSegment) spec.generators().get("bill");
        assertThat(bill.batch().initialOffset(), is(20_001L));
        // Offset 0 writes nothing: the 5.0 default 1 is the 4.x 0 + 1.
        assertThat(((GeneratorSpec.Segment) spec("segments-only").generators().get("invoice")).batch().initialOffset(), is(1L));

        // A migrated period continues from its 4.x value; the next, new period starts at offset + 1 as in 4.x.
        InMemoryStore store = new InMemoryStore();
        ManualClock clock = new ManualClock(LocalDate.of(2026, 10, 26).atTime(12, 0).atZone(bill.zone()).toInstant().toEpochMilli());
        store.reserve("shop/bill/20261026", 1, 20_500);
        try (PeriodicSegmentGenerator generator = new PeriodicSegmentGenerator("bill", "shop/bill", bill.period(), bill.zone(), store, bill.batch(),
            bill.periodic(), Runnable::run, clock, GeneratorListener.NOOP)) {
            assertThat(generator.generate(), is(new GroupedId("20261026", 20_501)));
            clock.advance(Duration.ofDays(1));
            assertThat(generator.generate(), is(new GroupedId("20261027", 20_001)));
        }
    }

    @Test
    void aProxyBecomesTheRemoteStoreWithItsOptions() throws IOException {
        RuntimeSpec spec = spec("edge");
        assertThat(spec.stores().get("remote").type(), is("remote"));
        assertThat(spec.stores().get("remote").options(), is(Map.of("baseUrl", "https://cosid-server.example:8688", "token", "secret-token",
            "connectTimeout", "3s", "requestTimeout", "4s")));
    }
}
