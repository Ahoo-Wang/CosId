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

import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.spring.autoconfigure.CosIdProperties;
import me.ahoo.cosid.spring.autoconfigure.CosIdSpecs;

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
import java.util.Map;

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
    }
}
