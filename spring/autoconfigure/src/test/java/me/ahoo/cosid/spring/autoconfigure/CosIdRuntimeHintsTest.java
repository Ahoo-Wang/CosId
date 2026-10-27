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

import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.observe.GeneratorInfo;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The native image hints and the generated configuration metadata.
 */
class CosIdRuntimeHintsTest {
    @Test
    void registersTheResourcesAndTypesTheStoresAndTheEndpointNeed() {
        RuntimeHints hints = new RuntimeHints();
        new CosIdRuntimeHints().registerHints(hints, getClass().getClassLoader());
        for (String resource : List.of("META-INF/services/me.ahoo.cosid.store.StoreProvider", "META-INF/services/me.ahoo.cosid.store.jdbc.JdbcDialect",
            "me/ahoo/cosid/store/jdbc/schema-mysql.sql", "me/ahoo/cosid/store/redis/reserve.lua")) {
            assertTrue(RuntimeHintsPredicates.resource().forResource(resource).test(hints), resource);
        }
        for (String provider : CosIdRuntimeHints.STORE_PROVIDERS) {
            assertTrue(RuntimeHintsPredicates.reflection().onType(org.springframework.aot.hint.TypeReference.of(provider))
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS).test(hints), provider);
        }
        assertTrue(RuntimeHintsPredicates.reflection().onType(CosIdGeneratorRegistrar.GeneratorBean.class).test(hints));
        assertTrue(RuntimeHintsPredicates.reflection().onType(GeneratorInfo.class).test(hints));
    }

    @Test
    void theConfigurationMetadataDescribesTheCosIdTree() throws IOException {
        String metadata;
        try (InputStream in = new ClassPathResource("META-INF/spring-configuration-metadata.json").getInputStream()) {
            metadata = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s", "");
        }
        for (String property : List.of(
            "\"name\":\"cosid.enabled\",\"type\":\"java.lang.Boolean\"",
            "\"defaultValue\":\"cosid\"",
            "\"name\":\"cosid.machine.leased.ttl\"",
            "\"defaultValue\":\"30s\"",
            "\"name\":\"cosid.machine.static.slot\"",
            "\"name\":\"cosid.machine.leased.preferred-slot\"",
            "\"name\":\"cosid.stores\"",
            "\"name\":\"cosid.generators\"",
            "\"name\":\"cosid.metrics.generated\"")) {
            assertTrue(metadata.contains(property), property);
        }
    }
}
