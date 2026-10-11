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

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class MongoMachineLeaseStoreTest {
    
    /**
     * A database that answers only {@code isMaster}, like a server before 4.2.10, and records the commands it got.
     */
    private static MongoDatabase databaseWithoutHello(List<String> commands, long localTime) {
        return (MongoDatabase) Proxy.newProxyInstance(MongoDatabase.class.getClassLoader(), new Class<?>[] {MongoDatabase.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "withTimeout":
                        return proxy;
                    case "getCollection":
                        return null;
                    case "runCommand":
                        String command = ((Document) args[0]).keySet().iterator().next();
                        commands.add(command);
                        if ("hello".equals(command)) {
                            BsonDocument response = new BsonDocument("ok", new BsonInt32(0))
                                .append("code", new BsonInt32(59))
                                .append("errmsg", new BsonString("no such command: 'hello'"));
                            throw new MongoCommandException(response, new ServerAddress());
                        }
                        return new Document("localTime", new Date(localTime));
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });
    }
    
    @Test
    void serverTimeFallsBackToIsMasterWithoutHello() {
        List<String> commands = new ArrayList<>();
        MongoMachineLeaseStore store = new MongoMachineLeaseStore(databaseWithoutHello(commands, 1_760_000_000_123L), Duration.ofSeconds(5));
        
        Assertions.assertEquals(1_760_000_000_123L, store.serverTime());
        Assertions.assertEquals(List.of("hello", "isMaster"), commands);
    }
}
