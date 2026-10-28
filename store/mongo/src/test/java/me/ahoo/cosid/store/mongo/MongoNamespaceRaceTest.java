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

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The E11000 paths of the first-use namespace upsert, made deterministic: the namespace collection is wrapped so its
 * {@code findOneAndUpdate} fails with a duplicate key, either before reaching the server (the winner's document is not
 * visible yet) or after it (the winner's document is there to read back).
 */
class MongoNamespaceRaceTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    private enum Mode { BEFORE, AFTER }

    private static MongoCommandException error(int code) {
        BsonDocument response = new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code))
            .append("errmsg", new BsonString("injected"));
        return new MongoCommandException(response, new ServerAddress());
    }

    /** The namespace collection, failing the next {@code failures} upserts with {@code code}. */
    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> failing(MongoCollection<Document> real, int failures, int code, Mode mode) {
        AtomicInteger left = new AtomicInteger(failures);
        return (MongoCollection<Document>) Proxy.newProxyInstance(MongoCollection.class.getClassLoader(), new Class<?>[] {MongoCollection.class},
            (proxy, method, args) -> {
                boolean fail = method.getName().equals("findOneAndUpdate") && left.getAndDecrement() > 0;
                if (fail && mode == Mode.BEFORE) {
                    throw error(code);
                }
                Object result;
                try {
                    result = method.invoke(real, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (fail) {
                    throw error(code);
                }
                return result;
            });
    }

    private static MongoSlotLeaseStore store(int failures, int code, Mode mode) {
        MongoDatabase database = MongoStore.configure(MongoTestSupport.database(), MongoTestSupport.TIMEOUT);
        return new MongoSlotLeaseStore(database.getCollection(MongoSlotLeaseStore.COLLECTION),
            failing(database.getCollection(MongoSlotLeaseStore.NAMESPACE_COLLECTION), failures, code, mode));
    }

    private static AcquireResult acquire(MongoSlotLeaseStore store, String namespace, int limit) {
        return acquire(store, namespace, limit, 0);
    }

    private static AcquireResult acquire(MongoSlotLeaseStore store, String namespace, int limit, int slotOffset) {
        return store.acquire(namespace, new Owner("host", UUID.randomUUID()), limit, TTL, TOLERANCE, slotOffset, OptionalInt.empty());
    }

    private static String newNamespace() {
        return "race-" + UUID.randomUUID();
    }

    @Test
    void lostUpsertReadsTheWinnerBack() {
        String namespace = newNamespace();
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store(1, MongoStore.DUPLICATE_KEY, Mode.AFTER), namespace, 4));
    }

    @Test
    void lostUpsertComparesTheWinnersParameters() {
        String namespace = newNamespace();
        acquire(new MongoSlotLeaseStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT), namespace, 4);
        assertThrows(NamespaceMismatchException.class, () -> acquire(store(1, MongoStore.DUPLICATE_KEY, Mode.AFTER), namespace, 8));
    }

    @Test
    void lostUpsertComparesTheWinnersSlotOffset() {
        String namespace = newNamespace();
        acquire(new MongoSlotLeaseStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT), namespace, 4, 1);
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store(1, MongoStore.DUPLICATE_KEY, Mode.AFTER), namespace, 4, 1));
        assertThrows(NamespaceMismatchException.class, () -> acquire(store(1, MongoStore.DUPLICATE_KEY, Mode.AFTER), namespace, 4, 0));
    }

    @Test
    void winnerNotVisibleYetUpsertsAgain() {
        String namespace = newNamespace();
        MongoSlotLeaseStore store = store(MongoSlotLeaseStore.NAMESPACE_ATTEMPTS - 1, MongoStore.DUPLICATE_KEY, Mode.BEFORE);
        assertInstanceOf(AcquireResult.Acquired.class, acquire(store, namespace, 4));
    }

    @Test
    void persistentDuplicateKeyIsRethrown() {
        MongoSlotLeaseStore store = store(MongoSlotLeaseStore.NAMESPACE_ATTEMPTS, MongoStore.DUPLICATE_KEY, Mode.BEFORE);
        MongoCommandException e = assertThrows(MongoCommandException.class, () -> acquire(store, newNamespace(), 4));
        assertEquals(MongoStore.DUPLICATE_KEY, e.getErrorCode());
    }

    @Test
    void otherServerErrorsAreNotRetried() {
        MongoSlotLeaseStore store = store(1, 2, Mode.BEFORE);
        assertEquals(2, assertThrows(MongoCommandException.class, () -> acquire(store, newNamespace(), 4)).getErrorCode());
    }
}
