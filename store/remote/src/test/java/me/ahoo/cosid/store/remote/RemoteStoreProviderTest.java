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

package me.ahoo.cosid.store.remote;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.ServiceLoader;

class RemoteStoreProviderTest {
    private static StoreConfig config(Map<String, Object> options) {
        return StoreConfig.builder("remote", RemoteStoreProvider.TYPE).timeout(Duration.ofSeconds(3)).options(options).build();
    }

    @Test
    void aTokenOverPlainHttpToAnotherHostIsWarnedAbout() {
        // R1-remote-03: judged by the url alone, no DNS.
        assertTrue(RemoteStoreProvider.plainToRemote(URI.create("http://cosid-server.cosid.svc:8688")));
        assertTrue(RemoteStoreProvider.plainToRemote(URI.create("http://10.0.0.5:8688")));
        assertFalse(RemoteStoreProvider.plainToRemote(URI.create("https://cosid-server:8688")));
        assertFalse(RemoteStoreProvider.plainToRemote(URI.create("http://localhost:8688")));
        assertFalse(RemoteStoreProvider.plainToRemote(URI.create("http://127.0.0.1:8688")));
        assertFalse(RemoteStoreProvider.plainToRemote(URI.create("http://[::1]:8688")));
        assertFalse(RemoteStoreProvider.plainToRemote(URI.create("HTTP://cosid.localhost:8688")));
        assertTrue(RemoteStoreProvider.plainToRemote(URI.create("http:cosid-server")));
        try (Store store = new RemoteStoreProvider().open(config(Map.of("baseUrl", "http://cosid-server.cosid.svc:8688", "token", "t")))) {
            assertEquals(URI.create("http://cosid-server.cosid.svc:8688"), assertInstanceOf(RemoteStore.class, store).baseUrl());
        }
    }

    @Test
    void isFoundByServiceLoader() {
        assertTrue(ServiceLoader.load(StoreProvider.class).stream().anyMatch(provider -> provider.get().type().equals(RemoteStoreProvider.TYPE) && provider.type() == RemoteStoreProvider.class));
    }

    @Test
    void opensAStoreOnTheBaseUrl() {
        try (Store store = new RemoteStoreProvider().open(config(Map.of("baseUrl", "http://localhost:8688/", "token", "t",
            "connectTimeout", "2s", "requestTimeout", 1500)))) {
            RemoteStore remote = assertInstanceOf(RemoteStore.class, store);
            assertEquals(URI.create("http://localhost:8688"), remote.baseUrl());
            assertInstanceOf(RemoteRangeStore.class, store.ranges());
            assertInstanceOf(RemoteSlotLeaseStore.class, store.slots());
        }
    }

    @Test
    void rejectsBadConfigs() {
        RemoteStoreProvider provider = new RemoteStoreProvider();
        assertThat(assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of()))).getMessage(), containsString("options.baseUrl"));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("baseUrl", " "))));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("baseUrl", "ftp://host"))));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("baseUrl", "localhost:8688"))));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("baseUrl", "http://h", "connectTimeout", "0s"))));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of("baseUrl", "http://h", "requestTimeout", "soon"))));
        StoreConfig withClient = StoreConfig.builder("remote", "remote")
            .timeout(Duration.ofSeconds(1))
            .option("baseUrl", "http://h")
            .client(new Object())
            .build();
        assertThat(assertThrows(IllegalArgumentException.class, () -> provider.open(withClient)).getMessage(), containsString("takes no client"));
    }

    @Test
    void parsesDurations() {
        Duration fallback = Duration.ofSeconds(9);
        assertEquals(fallback, RemoteStoreProvider.duration(config(Map.of()), "d", fallback));
        assertEquals(Duration.ofSeconds(2), RemoteStoreProvider.duration(config(Map.of("d", Duration.ofSeconds(2))), "d", fallback));
        assertEquals(Duration.ofMillis(250), RemoteStoreProvider.duration(config(Map.of("d", 250)), "d", fallback));
        assertEquals(Duration.ofMillis(250), RemoteStoreProvider.duration(config(Map.of("d", "250")), "d", fallback));
        assertEquals(Duration.ofSeconds(5), RemoteStoreProvider.duration(config(Map.of("d", "PT5S")), "d", fallback));
        assertEquals(Duration.ofMillis(5), RemoteStoreProvider.duration(config(Map.of("d", "5ms")), "d", fallback));
        assertEquals(Duration.ofSeconds(5), RemoteStoreProvider.duration(config(Map.of("d", "5s")), "d", fallback));
        assertEquals(Duration.ofMinutes(5), RemoteStoreProvider.duration(config(Map.of("d", "5m")), "d", fallback));
        assertEquals(Duration.ofHours(5), RemoteStoreProvider.duration(config(Map.of("d", " 5H ")), "d", fallback));
        assertThrows(IllegalArgumentException.class, () -> RemoteStoreProvider.duration(config(Map.of("d", "PTx")), "d", fallback));
    }
}
