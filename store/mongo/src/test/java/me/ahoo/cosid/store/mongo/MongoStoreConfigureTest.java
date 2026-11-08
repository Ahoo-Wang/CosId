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

import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class MongoStoreConfigureTest {

    /**
     * Records the timeouts handed to the driver; the database itself is never used.
     */
    private static List<String> driverTimeouts(Duration requested) {
        List<String> applied = new ArrayList<>();
        MongoDatabase database = (MongoDatabase) Proxy.newProxyInstance(MongoDatabase.class.getClassLoader(),
            new Class<?>[] {MongoDatabase.class},
            (proxy, method, values) -> switch (method.getName()) {
                case "withWriteConcern", "withReadConcern", "withReadPreference" -> proxy;
                case "withTimeout" -> {
                    applied.add(values[0] + " " + values[1]);
                    yield proxy;
                }
                default -> throw new AssertionError("Unexpected database call: " + method);
            });
        MongoStore.configure(database, requested);
        return applied;
    }

    @Test
    void aPositiveTimeoutNeverBecomesTheDriversNoTimeout() {
        // The driver reads 0 as "no timeout": a sub-millisecond timeout must become 1ms, not 0.
        assertEquals(List.of("1 MILLISECONDS"), driverTimeouts(Duration.ofNanos(1)));
        assertEquals(List.of("1 MILLISECONDS"), driverTimeouts(Duration.ofNanos(500_000)));
        assertEquals(List.of("1 MILLISECONDS"), driverTimeouts(Duration.ofMillis(1)));
        // Fractional milliseconds above 1ms keep the existing truncation.
        assertEquals(List.of("1 MILLISECONDS"), driverTimeouts(Duration.ofNanos(1_500_000)));
        assertEquals(List.of("5000 MILLISECONDS"), driverTimeouts(Duration.ofSeconds(5)));
    }
}
