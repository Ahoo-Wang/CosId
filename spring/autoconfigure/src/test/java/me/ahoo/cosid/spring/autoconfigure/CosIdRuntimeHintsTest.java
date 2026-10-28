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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The native image hints and the generated configuration metadata.
 */
class CosIdRuntimeHintsTest {
    /**
     * This module's generated metadata, whitespace stripped. Spring Boot's own jars carry a file of the same name, and which
     * one a plain classpath lookup returns depends on the classpath order, so read the one next to {@link CosIdProperties}.
     */
    private static String cosIdMetadata() throws IOException {
        String clazz = CosIdProperties.class.getName().replace('.', '/') + ".class";
        String location = CosIdProperties.class.getClassLoader().getResource(clazz).toString();
        URL metadata = URI.create(location.substring(0, location.length() - clazz.length()) + "META-INF/spring-configuration-metadata.json").toURL();
        try (InputStream in = metadata.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s", "");
        }
    }

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
        assertTrue(CosIdRuntimeHints.STORE_PROVIDERS.contains("me.ahoo.cosid.store.remote.RemoteStoreProvider"));
        assertTrue(RuntimeHintsPredicates.reflection().onType(me.ahoo.cosid.store.remote.RemoteStoreProvider.class)
            .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS).test(hints), "the remote store provider");
        assertTrue(RuntimeHintsPredicates.reflection().onType(CosIdGeneratorRegistrar.GeneratorBean.class).test(hints));
        assertTrue(RuntimeHintsPredicates.reflection().onType(GeneratorInfo.class).test(hints));
    }

    @Test
    void theStoreTypeHintsNameEveryStore() throws IOException {
        String metadata = cosIdMetadata();
        int hints = metadata.indexOf("\"name\":\"cosid.stores.values.type\"");
        assertTrue(hints >= 0, "the store type hints");
        String values = metadata.substring(hints, metadata.indexOf(']', hints));
        for (String type : List.of("jdbc", "redis", "mongo", "zookeeper", "remote")) {
            assertTrue(values.contains("\"value\":\"" + type + "\""), type);
        }
    }

    @Test
    void theConfigurationMetadataDescribesTheCosIdTree() throws IOException {
        String metadata = cosIdMetadata();
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
