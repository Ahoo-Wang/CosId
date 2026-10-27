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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The E11000 paths of {@link MongoRangeStore}, made deterministic: the collection is wrapped so a chosen call fails
 * with a duplicate key, after letting a "winner" act first, as the loser of a real upsert race would see it.
 */
class MongoRangeRaceTest {
    private final MongoCollection<Document> real = MongoStore.configure(MongoTestSupport.database(), MongoTestSupport.TIMEOUT)
        .getCollection(MongoRangeStore.COLLECTION);
    private final MongoRangeStore winner = new MongoRangeStore(real);

    private static String newKey() {
        return "mongo/race/" + UUID.randomUUID();
    }

    private static MongoCommandException error(int code) {
        BsonDocument response = new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code))
            .append("errmsg", new BsonString("injected"));
        return new MongoCommandException(response, new ServerAddress());
    }

    /**
     * The collection, failing the next {@code failures} calls of {@code method} with {@code code} before they reach the
     * server; {@code first} runs before each failure, as the winner of the race.
     */
    @SuppressWarnings("unchecked")
    private MongoCollection<Document> failing(String method, int failures, int code, Runnable first) {
        AtomicInteger left = new AtomicInteger(failures);
        return (MongoCollection<Document>) Proxy.newProxyInstance(MongoCollection.class.getClassLoader(), new Class<?>[] {MongoCollection.class},
            (proxy, called, args) -> {
                if (called.getName().equals(method) && left.getAndDecrement() > 0) {
                    first.run();
                    throw error(code);
                }
                try {
                    return called.invoke(real, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    private Document document(String key) {
        return real.find(Filters.eq(MongoRangeStore.ID, key)).first();
    }

    @Test
    void loserOfTheFirstReserveRetriesAfterTheWinner() {
        String key = newKey();
        Range[] won = new Range[1];
        MongoRangeStore loser = new MongoRangeStore(failing("findOneAndUpdate", 1, MongoStore.DUPLICATE_KEY,
            () -> won[0] = winner.reserve(key, 10, 100)));
        Range lost = loser.reserve(key, 10, 100);
        assertEquals(new Range(100, 110), won[0], "the winner creates the key at the offset");
        assertEquals(new Range(110, 120), lost, "the loser retries and reserves after it");
    }

    @Test
    void loserSeesAKeySealedDuringTheRace() {
        String key = newKey();
        MongoRangeStore loser = new MongoRangeStore(failing("findOneAndUpdate", 1, MongoStore.DUPLICATE_KEY, () -> {
            winner.reserve(key, 10, 0);
            winner.seal(key);
        }));
        assertThrows(RangeSealedException.class, () -> loser.reserve(key, 10, 0));
        assertEquals(10L, document(key).getLong(MongoRangeStore.NEXT), "the sealed key keeps its value");
    }

    @Test
    void winnerNotVisibleYetRetriesTheUpsert() {
        String key = newKey();
        MongoRangeStore loser = new MongoRangeStore(failing("findOneAndUpdate", MongoRangeStore.ATTEMPTS - 1, MongoStore.DUPLICATE_KEY, () -> { }));
        assertEquals(new Range(0, 10), loser.reserve(key, 10, 0));
    }

    @Test
    void persistentDuplicateKeyIsRethrownAfterTheAttempts() {
        String key = newKey();
        MongoRangeStore loser = new MongoRangeStore(failing("findOneAndUpdate", MongoRangeStore.ATTEMPTS, MongoStore.DUPLICATE_KEY, () -> { }));
        assertEquals(MongoStore.DUPLICATE_KEY, assertThrows(MongoCommandException.class, () -> loser.reserve(key, 10, 0)).getErrorCode());
        assertNull(document(key), "nothing was reserved");
    }

    @Test
    void otherServerErrorsAreNotRetried() {
        MongoRangeStore failing = new MongoRangeStore(failing("findOneAndUpdate", 1, 2, () -> { }));
        assertEquals(2, assertThrows(MongoCommandException.class, () -> failing.reserve(newKey(), 10, 0)).getErrorCode());
    }

    @Test
    void sealRetriesAConcurrentUpsertOfTheKey() {
        String key = newKey();
        MongoRangeStore sealing = new MongoRangeStore(failing("updateOne", 1, MongoStore.DUPLICATE_KEY, () -> winner.reserve(key, 5, 0)));
        assertTrue(sealing.seal(key));
        assertEquals(Boolean.TRUE, document(key).getBoolean(MongoRangeStore.SEALED));
        assertEquals(5L, document(key).getLong(MongoRangeStore.NEXT));
    }

    @Test
    void sealGivesUpOnAPersistentDuplicateKey() {
        MongoRangeStore sealing = new MongoRangeStore(failing("updateOne", MongoRangeStore.ATTEMPTS, MongoStore.DUPLICATE_KEY, () -> { }));
        assertEquals(MongoStore.DUPLICATE_KEY, assertThrows(MongoCommandException.class, () -> sealing.seal(newKey())).getErrorCode());
    }
}
