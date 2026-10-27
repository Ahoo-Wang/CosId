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

package me.ahoo.cosid.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.store.tck.InMemoryStore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which store backs the server: the one of {@code cosid.stores} that {@code cosid.server.store} names, or the only one.
 */
class BackingStoreTest {
    private static final StoreSpec MEMORY = new StoreSpec("memory", Duration.ofSeconds(1), Map.of());

    @Test
    void takesTheOnlyStoreAndTheProviderBean() {
        InMemoryStore memory = new InMemoryStore();
        try (BackingStore store = BackingStore.open(Map.of("mem", MEMORY), null, List.of(new TestServer.MemoryProvider(memory)), 4)) {
            assertEquals("mem", store.name());
            assertSame(memory, store.ranges());
            assertSame(memory, store.slots());
        }
    }

    @Test
    void needsANameAmongSeveralStores() {
        Map<String, StoreSpec> stores = Map.of("a", MEMORY, "b", MEMORY);
        List<TestServer.MemoryProvider> providers = List.of(new TestServer.MemoryProvider(new InMemoryStore()));
        assertThat(assertThrows(CosIdException.class, () -> BackingStore.open(stores, " ", List.copyOf(providers), 4)).getMessage(),
            containsString("name the backing store"));
        assertThat(assertThrows(CosIdException.class, () -> BackingStore.open(stores, "c", List.copyOf(providers), 4)).getMessage(),
            containsString("no store [c]"));
        try (BackingStore store = BackingStore.open(stores, "b", List.copyOf(providers), 4)) {
            assertEquals("b", store.name());
        }
    }

    @Test
    void unknownTypeFails() {
        Map<String, StoreSpec> stores = Map.of("x", new StoreSpec("nowhere", Duration.ofSeconds(1), Map.of()));
        assertThat(assertThrows(CosIdException.class, () -> BackingStore.open(stores, null, List.of(), 4)).getMessage(),
            containsString("No StoreProvider of type [nowhere]"));
    }

    @Test
    void jdbcStoreGetsAPoolThatClosesWithIt() {
        String url = "jdbc:h2:mem:backing-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        StoreSpec jdbc = new StoreSpec("jdbc", Duration.ofSeconds(3), Map.of("url", url, "username", "sa", "password", "", "initSchema", "true"));
        try (BackingStore store = BackingStore.open(Map.of("main", jdbc), "main", List.of(), 2)) {
            assertEquals(0, store.ranges().reserve("ns/a", 3, 0).start());
        }
        StoreSpec broken = new StoreSpec("jdbc", Duration.ofSeconds(3), Map.of("url", url, "username", "sa", "dialect", "unknown"));
        assertThrows(IllegalArgumentException.class, () -> BackingStore.open(Map.of("main", broken), "main", List.of(), 2));
    }

    @Test
    void serverRefusesToStartWithoutAStore() {
        Exception error = assertThrows(Exception.class, () -> TestServer.start(null));
        assertTrue(AuthTest.rootMessage(error).contains("needs a backing store"), () -> AuthTest.rootMessage(error));
    }
}
