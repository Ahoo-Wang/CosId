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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.StoreTimeoutException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoOperationTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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

    @Test
    void writesWithMajorityByDefault() {
        try (MongoStore store = open(Map.of())) {
            assertConcerns(store, WriteConcern.MAJORITY);
        }
        assertEquals(WriteConcern.MAJORITY, MongoStoreProvider.writeConcern("ids", null));
    }

    @Test
    void parsesTheStandardWriteConcernNamesInAnyCase() {
        assertEquals(WriteConcern.MAJORITY, MongoStoreProvider.writeConcern("ids", "Majority"));
        assertEquals(WriteConcern.W1, MongoStoreProvider.writeConcern("ids", "w1"));
        assertEquals(WriteConcern.W2, MongoStoreProvider.writeConcern("ids", "W2"));
        assertEquals(WriteConcern.W3, MongoStoreProvider.writeConcern("ids", "w3"));
        assertEquals(WriteConcern.JOURNALED, MongoStoreProvider.writeConcern("ids", "JOURNALED"));
        assertEquals(WriteConcern.ACKNOWLEDGED, MongoStoreProvider.writeConcern("ids", "acknowledged"));
        assertEquals(new WriteConcern(5), MongoStoreProvider.writeConcern("ids", "5"));
        assertEquals(new WriteConcern(2), MongoStoreProvider.writeConcern("ids", 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unacknowledged", "0", "-1", "none", "", " ", "99999999999", "w0"})
    void rejectsUnknownOrUnacknowledgedWriteConcerns(String value) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MongoStoreProvider.writeConcern("ids", value));
        assertThat(e.getMessage(), containsString("options.writeConcern"));
        assertThrows(IllegalArgumentException.class, () -> open(Map.of(MongoStoreProvider.WRITE_CONCERN, value)));
    }

    @Test
    void rejectsAWriteConcernOfAnotherType() {
        assertThrows(IllegalArgumentException.class, () -> MongoStoreProvider.writeConcern("ids", List.of("majority")));
    }

    @Test
    void appliesTheConfiguredWriteConcernToEveryCollection() {
        String key = "provider/" + UUID.randomUUID();
        try (MongoStore store = open(Map.of(MongoStoreProvider.WRITE_CONCERN, "w1"))) {
            assertConcerns(store, WriteConcern.W1);
            assertEquals(new Range(0, 3), store.ranges().reserve(key, 3, 0), "w1 is acknowledged by the primary alone");
        }
    }

    @Test
    void warnsAtStartupWhenTheWriteConcernIsWeakerThanMajority() {
        assertThat(warnings(Map.of()), empty());
        assertThat(warnings(Map.of(MongoStoreProvider.WRITE_CONCERN, "MAJORITY")), empty());
        for (String weaker : List.of("w1", "2", "acknowledged", "journaled")) {
            List<String> warnings = warnings(Map.of(MongoStoreProvider.WRITE_CONCERN, weaker));
            assertEquals(1, warnings.size(), weaker);
            assertThat(warnings.get(0), containsString("weaker than majority"));
            assertThat(warnings.get(0), containsString("duplicate ids"));
        }
        assertFalse(MongoStore.isWeakerThanMajority(WriteConcern.MAJORITY.withJournal(true)));
        assertTrue(MongoStore.isWeakerThanMajority(WriteConcern.W3));
    }

    private static MongoStore open(Map<String, Object> options) {
        Map<String, Object> all = new HashMap<>(options);
        all.put(MongoStoreProvider.DATABASE, MongoTestSupport.DATABASE);
        return (MongoStore) new MongoStoreProvider().open(config(all, Optional.of(MongoTestSupport.client())));
    }

    private static void assertConcerns(MongoStore store, WriteConcern expected) {
        MongoRangeStore ranges = (MongoRangeStore) store.ranges();
        MongoSlotLeaseStore slots = (MongoSlotLeaseStore) store.slots();
        for (MongoCollection<Document> collection : List.of(ranges.collection(), slots.slotCollection(), slots.namespaceCollection())) {
            assertEquals(expected, collection.getWriteConcern(), collection.getNamespace().getFullName());
            assertEquals(ReadConcern.MAJORITY, collection.getReadConcern(), "reads stay majority");
        }
    }

    private static List<String> warnings(Map<String, Object> options) {
        Logger logger = (Logger) LoggerFactory.getLogger(MongoStoreProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            open(options).close();
            return appender.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void anOperationThatRunsOutOfTheTimeoutFailsWithStoreTimeoutException() {
        // 1ms: some operations still make it, but none fails otherwise, and the ones that do not are store timeouts.
        StoreConfig tight = new StoreConfig("ids", MongoStoreProvider.TYPE, Duration.ofMillis(1), Map.of(MongoStoreProvider.DATABASE,
            MongoTestSupport.DATABASE), Optional.of(MongoTestSupport.client()));
        int timeouts = 0;
        try (Store store = new MongoStoreProvider().open(tight)) {
            for (int i = 0; i < 50 && timeouts == 0; i++) {
                try {
                    store.ranges().reserve("provider/" + UUID.randomUUID(), 1, 0);
                } catch (StoreTimeoutException e) {
                    assertInstanceOf(MongoTimeoutException.class, e.getCause());
                    timeouts++;
                }
            }
        }
        assertTrue(timeouts > 0, "a 1ms timeout runs out");
    }

    @Test
    void mapsTheDriverTimeoutsOnly() {
        MongoOperationTimeoutException client = new MongoOperationTimeoutException("timeoutMS expired");
        assertThat(assertThrows(StoreTimeoutException.class, () -> MongoStore.timed("reserve", () -> {
            throw client;
        })).getMessage(), containsString("MongoDB reserve timed out"));
        assertThrows(StoreTimeoutException.class, () -> MongoStore.timed("renew", () -> {
            throw new MongoExecutionTimeoutException(50, "operation exceeded time limit");
        }));
        IllegalStateException other = new IllegalStateException("not a timeout");
        assertEquals(other, assertThrows(IllegalStateException.class, () -> MongoStore.timed("seal", () -> {
            throw other;
        })));
        assertEquals("ok", MongoStore.timed("seal", () -> "ok"));
    }

    @Test
    void storeTimeoutBoundsAnOperationOnAServerThatStoppedAnswering() {
        // A replica set of its own: pausing it freezes the server with its sockets open, so only the store timeout ends the call.
        String image = System.getenv("COSID_TEST_MONGODB_IMAGE");
        try (MongoDBContainer mongo = new MongoDBContainer(image == null || image.isBlank() ? MongoTestSupport.DEFAULT_IMAGE : image).withReplicaSet()) {
            mongo.start();
            String key = "timeout/" + UUID.randomUUID();
            Map<String, Object> options = Map.of(MongoStoreProvider.DATABASE, MongoTestSupport.DATABASE);
            // One client for both stores: the first, cold operation (server discovery, connection, collection creation) runs
            // under the generous timeout, so the 500 ms store reaches a warm pool and only the paused call is bounded by it.
            try (MongoClient client = MongoClients.create(mongo.getReplicaSetUrl(MongoTestSupport.DATABASE));
                 Store warmUp = new MongoStoreProvider().open(config(options, Optional.of(client)));
                 Store store = new MongoStoreProvider().open(
                     new StoreConfig("paused", MongoStoreProvider.TYPE, Duration.ofMillis(500), options, Optional.of(client)))) {
                assertEquals(new Range(0, 1), warmUp.ranges().reserve(key, 1, 0));
                var docker = DockerClientFactory.instance().client();
                docker.pauseContainerCmd(mongo.getContainerId()).exec();
                try {
                    long start = System.nanoTime();
                    StoreTimeoutException timeout = assertThrows(StoreTimeoutException.class, () -> store.ranges().reserve(key, 1, 0));
                    assertInstanceOf(MongoOperationTimeoutException.class, timeout.getCause());
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    assertTrue(millis >= 450 && millis < 5_000, "the store timeout bounds the call: " + millis + " ms");
                } finally {
                    docker.unpauseContainerCmd(mongo.getContainerId()).exec();
                }
            }
        }
    }
}
