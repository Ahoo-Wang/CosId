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

package me.ahoo.cosid.mongo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;

import me.ahoo.cosid.test.container.MongoLauncher;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * A segment fetch is one write that creates the document at its offset and stamps the server's clock, on a real MongoDB.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoIdSegmentCollectionContractTest {
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;
    MongoCollection<Document> cosidCollection;
    MongoIdSegmentCollection collection;
    
    @BeforeAll
    void setup() {
        mongoClient = MongoClients.create(MongoLauncher.getConnectionString());
        mongoDatabase = mongoClient.getDatabase("cosid_db_segment_" + UUID.randomUUID().toString().replace("-", ""));
        cosidCollection = mongoDatabase.getCollection("cosid");
        collection = new MongoIdSegmentCollection(cosidCollection);
    }
    
    @AfterAll
    void destroy() {
        if (mongoDatabase != null) {
            mongoDatabase.drop();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
    
    @Test
    void theFirstFetchStartsAtTheOffset() {
        long before = System.currentTimeMillis();
        assertThat(collection.incrementAndGet("ns.first", 7, 100), equalTo(107L));
        assertThat(collection.incrementAndGet("ns.first", 7, 100), equalTo(207L));
        
        Document document = cosidCollection.find(Filters.eq(Documents.ID_FIELD, "ns.first")).first();
        // The document format of 3.x and 4.0: two longs.
        assertThat(document.keySet(), equalTo(Set.of(Documents.ID_FIELD, IdSegmentOperates.LAST_MAX_ID_FIELD, IdSegmentOperates.LAST_FETCH_TIME_FIELD)));
        assertThat(document.get(IdSegmentOperates.LAST_MAX_ID_FIELD), instanceOf(Long.class));
        assertThat(document.get(IdSegmentOperates.LAST_FETCH_TIME_FIELD), instanceOf(Long.class));
        long fetchTime = document.getLong(IdSegmentOperates.LAST_FETCH_TIME_FIELD);
        // The server's clock: close to this host's, in milliseconds.
        assertThat(fetchTime, greaterThan(before - TimeUnit.MINUTES.toMillis(1)));
        assertThat(fetchTime, lessThan(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)));
    }
    
    @Test
    void anExistingSegmentKeepsItsMaxId() {
        cosidCollection.insertOne(IdSegmentOperates.ensureIdSegmentDocument("ns.existing", 1000));
        assertThat(collection.incrementAndGet("ns.existing", 7, 10), equalTo(1010L));
    }
    
    @Test
    void concurrentFirstFetchesGetDistinctSegments() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Set<Long> maxIds = ConcurrentHashMap.newKeySet();
            List<Future<Boolean>> fetches = IntStream.range(0, 32)
                .mapToObj(i -> executor.submit(() -> maxIds.add(collection.incrementAndGet("ns.concurrent", 0, 10))))
                .toList();
            for (Future<Boolean> fetch : fetches) {
                fetch.get(30, TimeUnit.SECONDS);
            }
            assertThat(maxIds.size(), equalTo(32));
            assertThat(maxIds.stream().mapToLong(Long::longValue).max().orElseThrow(), equalTo(320L));
        } finally {
            executor.shutdownNow();
        }
    }
}
