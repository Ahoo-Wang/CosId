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

package me.ahoo.cosid.store.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.UUID;

class MongoStoreProviderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static StoreConfig config(Map<String, Object> options, Optional<Object> client) {
        return new StoreConfig("ids", MongoStoreProvider.TYPE, TIMEOUT, options, client);
    }

    @Test
    void isFoundByServiceLoader() {
        assertTrue(ServiceLoader.load(StoreProvider.class).stream()
            .anyMatch(provider -> provider.get().type().equals("mongo") && provider.type() == MongoStoreProvider.class));
    }

    @Test
    void usesAnInjectedClientAndLeavesItOpen() {
        String key = "provider/" + UUID.randomUUID();
        try (Store store = new MongoStoreProvider().open(config(Map.of(MongoStoreProvider.DATABASE, MongoTestSupport.DATABASE),
            Optional.of(MongoTestSupport.client())))) {
            assertEquals(new Range(0, 10), store.ranges().reserve(key, 10, 0));
            assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire("provider-" + UUID.randomUUID(),
                new Owner("host-a", UUID.randomUUID()), 4, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty()));
        }
        Document stored = MongoTestSupport.database().getCollection(MongoRangeStore.COLLECTION).find(Filters.eq(MongoRangeStore.ID, key)).first();
        assertEquals(10L, stored.getLong(MongoRangeStore.NEXT), "the injected client is still open, on the configured database");
    }

    @Test
    void buildsAClientFromTheUriOnTheDefaultDatabase() {
        String key = "provider/" + UUID.randomUUID();
        try (Store store = new MongoStoreProvider().open(config(Map.of(MongoStoreProvider.URI, MongoTestSupport.uri()), Optional.empty()))) {
            assertEquals(new Range(5, 6), store.ranges().reserve(key, 1, 5));
        }
        Document stored = MongoTestSupport.client().getDatabase(MongoStore.DEFAULT_DATABASE).getCollection(MongoRangeStore.COLLECTION)
            .find(Filters.eq(MongoRangeStore.ID, key)).first();
        assertEquals(6L, stored.getLong(MongoRangeStore.NEXT));
    }

    @Test
    void rejectsBadConfigurations() {
        MongoStoreProvider provider = new MongoStoreProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(), Optional.empty())), "no uri, no client");
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(), Optional.of("not a client"))));
        assertThrows(IllegalArgumentException.class, () -> provider.open(config(Map.of(MongoStoreProvider.URI, 42), Optional.empty())));
        assertThrows(IllegalArgumentException.class,
            () -> provider.open(config(Map.of(MongoStoreProvider.DATABASE, " "), Optional.of(MongoTestSupport.client()))));
    }
}
