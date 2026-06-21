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

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

class MongoIdSegmentCollectionTest {

    @Test
    void incrementAndGetShouldIgnoreDuplicateKeyFromEnsureOffset() {
        AtomicBoolean findOneAndUpdateCalled = new AtomicBoolean(false);
        MongoIdSegmentCollection collection = new MongoIdSegmentCollection(
            cosidCollection(duplicateKeyException(), 107, findOneAndUpdateCalled)
        );

        long actual = collection.incrementAndGet("ns.name", 7, 100);

        Assertions.assertEquals(107, actual);
        Assertions.assertTrue(findOneAndUpdateCalled.get());
    }

    @Test
    void incrementAndGetShouldRethrowNonDuplicateWriteExceptionFromEnsureOffset() {
        MongoWriteException writeException = writeException(1);
        AtomicBoolean findOneAndUpdateCalled = new AtomicBoolean(false);
        MongoIdSegmentCollection collection = new MongoIdSegmentCollection(
            cosidCollection(writeException, 107, findOneAndUpdateCalled)
        );

        MongoWriteException actual = Assertions.assertThrows(MongoWriteException.class,
            () -> collection.incrementAndGet("ns.name", 7, 100));

        Assertions.assertSame(writeException, actual);
        Assertions.assertFalse(findOneAndUpdateCalled.get());
    }

    private static MongoCollection<Document> cosidCollection(MongoWriteException ensureOffsetException,
                                                            long lastMaxId,
                                                            AtomicBoolean findOneAndUpdateCalled) {
        return proxy(MongoCollection.class, (proxy, method, args) -> {
            String methodName = method.getName();
            if ("updateOne".equals(methodName)) {
                throw ensureOffsetException;
            }
            if ("findOneAndUpdate".equals(methodName)) {
                findOneAndUpdateCalled.set(true);
                return new Document(IdSegmentOperates.LAST_MAX_ID_FIELD, lastMaxId);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static MongoWriteException duplicateKeyException() {
        return writeException(11000);
    }

    private static MongoWriteException writeException(int code) {
        return new MongoWriteException(new WriteError(code, "write failed", new BsonDocument()), new ServerAddress());
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
