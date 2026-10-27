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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.migration.config.ConfigConverter;
import me.ahoo.cosid.migration.config.FlatConfig;
import me.ahoo.cosid.migration.config.Legacy4x;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.spring.autoconfigure.CosIdProperties;
import me.ahoo.cosid.spring.autoconfigure.CosIdSpecs;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.remote.RemoteStoreProvider;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;
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

    @Test
    void aProxyBecomesTheRemoteStoreWithItsOptions() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("edge",
            new FileSystemResource(MigrationScenarioTest.resource("config/edge/expected.yaml")))) {
            environment.getPropertySources().addFirst(source);
        }
        environment.getPropertySources().addLast(new MapPropertySource("slots", Map.of("POD_ORDINAL", "3")));
        RuntimeSpec spec = CosIdSpecs.toSpec(Binder.get(environment).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class)),
            ZoneId.of("UTC"));
        assertThat(spec.stores().get("remote").type(), is("remote"));
        assertThat(spec.stores().get("remote").options(), is(Map.of("baseUrl", "https://cosid-server.example:8688", "token", "secret-token",
            "connectTimeout", "3s", "requestTimeout", "4s")));
    }
}
