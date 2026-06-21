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

package me.ahoo.cosid.mongo.reactive;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;

import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class MongoReactiveMachineCollectionTest {
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", false);
    private static final MachineState MACHINE_STATE = MachineState.of(1, 100);

    @Test
    void revertShouldThrowWhenNoDocumentMatched() {
        MongoReactiveMachineCollection collection = new MongoReactiveMachineCollection(
            machineCollection(UpdateResult.acknowledged(0, 0L, null))
        );

        Assertions.assertThrows(MachineIdLostException.class,
            () -> collection.revert("ns", INSTANCE_ID, MACHINE_STATE));
    }

    @Test
    void revertShouldUseMatchedCountWhenUpdateIsNoop() {
        MongoReactiveMachineCollection collection = new MongoReactiveMachineCollection(
            machineCollection(UpdateResult.acknowledged(1, 0L, null))
        );

        Assertions.assertDoesNotThrow(() -> collection.revert("ns", INSTANCE_ID, MACHINE_STATE));
    }

    private static MongoCollection<Document> machineCollection(UpdateResult updateResult) {
        return proxy(MongoCollection.class, (proxy, method, args) -> {
            if ("updateOne".equals(method.getName())) {
                return Mono.just(updateResult);
            }
            return defaultValue(method.getReturnType());
        });
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
        return Mono.empty();
    }
}
