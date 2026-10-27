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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the range contract does not pin down for MongoDB: the E11000 retry of a lost upsert race, the sealed filter
 * and the overflow guard, checked on the documents themselves.
 */
class MongoRangeStoreBehaviorTest {
    private final MongoRangeStore store = new MongoRangeStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT);
    private final MongoCollection<Document> ranges = MongoTestSupport.database().getCollection(MongoRangeStore.COLLECTION);

    private static String newKey() {
        return "mongo/range/" + UUID.randomUUID();
    }

    private Document document(String key) {
        return ranges.find(Filters.eq(MongoRangeStore.ID, key)).first();
    }

    /**
     * Concurrent first reserves of fresh keys from separate clients: the losers of the upsert race get E11000 from the
     * server (the filter on sealed and next keeps the server from retrying the upsert itself) and retry. Every key still
     * hands out gapless, disjoint ranges from the offset.
     */
    @Test
    void lostUpsertRacesRetryAndNeverOverlap() throws Exception {
        int threads = 8;
        AtomicInteger races = new AtomicInteger();
        CommandListener duplicateKeys = new CommandListener() {
            @Override
            public void commandFailed(CommandFailedEvent event) {
                if (event.getThrowable() instanceof MongoCommandException e && e.getErrorCode() == MongoStore.DUPLICATE_KEY) {
                    races.incrementAndGet();
                }
            }
        };
        List<MongoClient> clients = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<MongoRangeStore> stores = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                MongoClient client = MongoClients.create(MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(MongoTestSupport.uri())).addCommandListener(duplicateKeys).build());
                clients.add(client);
                stores.add(new MongoRangeStore(client.getDatabase(MongoTestSupport.DATABASE), MongoTestSupport.TIMEOUT));
            }
            for (int round = 0; round < 50; round++) {
                String key = newKey();
                CyclicBarrier barrier = new CyclicBarrier(threads);
                List<Callable<Range>> calls = new ArrayList<>();
                for (MongoRangeStore own : stores) {
                    calls.add(() -> {
                        barrier.await();
                        return own.reserve(key, 5, 100);
                    });
                }
                List<Range> reserved = new ArrayList<>();
                for (Future<Range> future : executor.invokeAll(calls)) {
                    reserved.add(future.get());
                }
                reserved.sort(Comparator.comparingLong(Range::start));
                for (int i = 0; i < threads; i++) {
                    assertEquals(new Range(100 + 5L * i, 105 + 5L * i), reserved.get(i), "no gap and no overlap: " + reserved);
                }
            }
        } finally {
            executor.shutdownNow();
            clients.forEach(MongoClient::close);
        }
        LoggerFactory.getLogger(MongoRangeStoreBehaviorTest.class).info("{} first reserves lost the upsert race and retried", races.get());
        assertTrue(races.get() > 0, "8 clients racing on 50 new keys exercised the E11000 retry");
    }

    @Test
    void reserveOfASealedKeyMeetsTheDuplicateKeyAndReportsTheSeal() {
        String key = newKey();
        store.seal(key);
        // The filter skips the sealed document, so the upsert inserts _id = key again: E11000, then the read sees the seal.
        assertThrows(RangeSealedException.class, () -> store.reserve(key, 1, 0));
        assertThrows(RangeSealedException.class, () -> store.reserve(key, 1, 0));
    }

    @Test
    void sealedKeyIsFilteredOutAndKeepsItsValue() {
        String key = newKey();
        store.reserve(key, 10, 0);
        store.seal(key);
        assertThrows(RangeSealedException.class, () -> store.reserve(key, 10, 0));
        Document sealed = document(key);
        assertEquals(10L, sealed.getLong(MongoRangeStore.NEXT), "the sealed filter left next alone");
        assertEquals(Boolean.TRUE, sealed.getBoolean(MongoRangeStore.SEALED));
    }

    @Test
    void sealingAnUnknownKeyNeverCreatesIt() {
        String key = newKey();
        store.seal(key);
        assertThrows(RangeSealedException.class, () -> store.reserve(key, 1, 0));
        assertThat(document(key).get(MongoRangeStore.NEXT), nullValue());
    }

    @Test
    void newKeyBeyondLongMaxIsNeverCreated() {
        String key = newKey();
        assertThrows(RangeExhaustedException.class, () -> store.reserve(key, 10, Long.MAX_VALUE - 5));
        assertThat(document(key), nullValue());
        assertEquals(new Range(Long.MAX_VALUE - 5, Long.MAX_VALUE), store.reserve(key, 5, Long.MAX_VALUE - 5));
    }

    @Test
    void storesNextAsInt64() {
        String key = newKey();
        store.reserve(key, 3, 1);
        assertThat(document(key).get(MongoRangeStore.NEXT), equalTo(4L));
    }
}
