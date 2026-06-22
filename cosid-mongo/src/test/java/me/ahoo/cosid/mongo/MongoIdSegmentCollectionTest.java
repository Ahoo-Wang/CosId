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
import static org.hamcrest.Matchers.sameInstance;

import com.mongodb.MongoWriteException;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

class MongoIdSegmentCollectionTest {

    @Test
    void incrementAndGetShouldIgnoreDuplicateKeyFromEnsureOffset() {
        RecordingMongoCollection recordingCollection = new RecordingMongoCollection()
            .throwOnEnsureOffset(duplicateKeyException())
            .returnLastMaxId(107);
        MongoIdSegmentCollection collection = new MongoIdSegmentCollection(recordingCollection.mongoCollection());

        long actual = collection.incrementAndGet("ns.name", 7, 100);

        assertThat(actual, equalTo(107L));
        assertThat(recordingCollection.findOneAndUpdateCalls, equalTo(1));
    }

    @Test
    void incrementAndGetShouldRethrowNonDuplicateWriteExceptionFromEnsureOffset() {
        MongoWriteException writeException = writeException(1);
        RecordingMongoCollection recordingCollection = new RecordingMongoCollection()
            .throwOnEnsureOffset(writeException)
            .returnLastMaxId(107);
        MongoIdSegmentCollection collection = new MongoIdSegmentCollection(recordingCollection.mongoCollection());

        MongoWriteException actual = Assertions.assertThrows(MongoWriteException.class,
            () -> collection.incrementAndGet("ns.name", 7, 100));

        assertThat(actual, sameInstance(writeException));
        assertThat(recordingCollection.findOneAndUpdateCalls, equalTo(0));
    }

    @Test
    void incrementAndGetShouldUseIdFilterAndUpsertOptions() {
        RecordingMongoCollection recordingCollection = new RecordingMongoCollection().returnLastMaxId(107);
        MongoIdSegmentCollection collection = new MongoIdSegmentCollection(recordingCollection.mongoCollection());

        collection.incrementAndGet("ns.name", 7, 100);

        assertIdFilter(recordingCollection.ensureFilter, "ns.name");
        assertIdFilter(recordingCollection.incrementFilter, "ns.name");
        assertThat(recordingCollection.ensureOptions.isUpsert(), equalTo(true));
        assertThat(recordingCollection.incrementOptions.isUpsert(), equalTo(true));
        assertThat(recordingCollection.incrementOptions.getReturnDocument(), equalTo(ReturnDocument.AFTER));
        assertThat(recordingCollection.incrementOptions.getMaxTime(TimeUnit.MILLISECONDS),
            equalTo(me.ahoo.cosid.mongo.reactive.BlockingAdapter.DEFAULT_TIME_OUT.toMillis()));
    }

    @Test
    void incrementAndGetShouldSetOffsetOnlyOnInsertAndIncrementLastMaxId() {
        RecordingMongoCollection recordingCollection = new RecordingMongoCollection().returnLastMaxId(107);
        MongoIdSegmentCollection collection = new MongoIdSegmentCollection(recordingCollection.mongoCollection());

        collection.incrementAndGet("ns.name", 7, 100);

        BsonDocument ensureUpdate = bson(recordingCollection.ensureUpdate);
        BsonDocument setOnInsert = ensureUpdate.getDocument("$setOnInsert");
        assertThat(setOnInsert.getInt64(IdSegmentOperates.LAST_MAX_ID_FIELD).longValue(), equalTo(7L));
        assertThat(setOnInsert.getInt64(IdSegmentOperates.LAST_FETCH_TIME_FIELD).longValue(), equalTo(0L));

        BsonDocument incrementUpdate = bson(recordingCollection.incrementUpdate);
        assertThat(incrementUpdate.getDocument("$inc").getInt64(IdSegmentOperates.LAST_MAX_ID_FIELD).longValue(), equalTo(100L));
        Assertions.assertTrue(incrementUpdate.getDocument("$set").containsKey(IdSegmentOperates.LAST_FETCH_TIME_FIELD));
    }

    private static MongoWriteException duplicateKeyException() {
        return writeException(11000);
    }

    private static MongoWriteException writeException(int code) {
        return new MongoWriteException(new WriteError(code, "write failed", new BsonDocument()), new ServerAddress());
    }

    private static void assertIdFilter(Bson filter, String namespacedName) {
        assertThat(bson(filter).getString(Documents.ID_FIELD).getValue(), equalTo(namespacedName));
    }

    private static BsonDocument bson(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    private static final class RecordingMongoCollection {
        private MongoWriteException ensureOffsetException;
        private long lastMaxId;
        private Bson ensureFilter;
        private Bson ensureUpdate;
        private UpdateOptions ensureOptions;
        private Bson incrementFilter;
        private Bson incrementUpdate;
        private FindOneAndUpdateOptions incrementOptions;
        private int findOneAndUpdateCalls;

        RecordingMongoCollection throwOnEnsureOffset(MongoWriteException ensureOffsetException) {
            this.ensureOffsetException = ensureOffsetException;
            return this;
        }

        RecordingMongoCollection returnLastMaxId(long lastMaxId) {
            this.lastMaxId = lastMaxId;
            return this;
        }

        MongoCollection<Document> mongoCollection() {
            return proxy(MongoCollection.class, this::invoke);
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("updateOne".equals(methodName)) {
                ensureFilter = (Bson) args[0];
                ensureUpdate = (Bson) args[1];
                ensureOptions = (UpdateOptions) args[2];
                if (ensureOffsetException != null) {
                    throw ensureOffsetException;
                }
                return UpdateResult.acknowledged(1, 1L, null);
            }
            if ("findOneAndUpdate".equals(methodName)) {
                findOneAndUpdateCalls++;
                incrementFilter = (Bson) args[0];
                incrementUpdate = (Bson) args[1];
                incrementOptions = (FindOneAndUpdateOptions) args[2];
                return new Document(IdSegmentOperates.LAST_MAX_ID_FIELD, lastMaxId);
            }
            return defaultValue(method.getReturnType());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return objectValue(type, proxy, method, args);
            }
            return handler.invoke(proxy, method, args);
        });
    }

    private static Object objectValue(Class<?> type, Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> type.getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(Boolean.TYPE)) {
            return false;
        }
        if (returnType.equals(Integer.TYPE)) {
            return 0;
        }
        if (returnType.equals(Long.TYPE)) {
            return 0L;
        }
        if (UpdateResult.class.equals(returnType)) {
            return UpdateResult.acknowledged(1, 1L, null);
        }
        return null;
    }
}
