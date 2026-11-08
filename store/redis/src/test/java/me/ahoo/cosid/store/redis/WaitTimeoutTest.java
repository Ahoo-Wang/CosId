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

package me.ahoo.cosid.store.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * The {@code WAIT} timeout is the store timeout in whole milliseconds, rounded up: Redis reads {@code WAIT n 0} as "wait
 * forever", so a positive timeout below 1 ms must not become 0. The connection is an in-memory double: no Redis.
 */
class WaitTimeoutTest {
    private static final List<Object> REPLY = List.of("reserved", 1L, 2L);

    /** The {@code timeout} argument of each {@code waitForReplication} the scripts sent, on a store with one replica. */
    private static List<Long> waitTimeouts(Duration timeout) {
        List<Long> waits = new CopyOnWriteArrayList<>();
        @SuppressWarnings("unchecked")
        RedisAsyncCommands<String, String> async = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(WaitTimeoutTest.class.getClassLoader(),
            new Class<?>[] {RedisAsyncCommands.class}, (proxy, method, args) -> {
                Done<Object> done = new Done<>();
                switch (method.getName()) {
                    case "evalsha" -> done.complete(REPLY);
                    case "waitForReplication" -> {
                        waits.add((Long) args[1]);
                        done.complete(1L);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
                return done;
            });
        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> connection = (StatefulRedisConnection<String, String>) Proxy.newProxyInstance(
            WaitTimeoutTest.class.getClassLoader(), new Class<?>[] {StatefulRedisConnection.class},
            (proxy, method, args) -> "async".equals(method.getName()) ? async : null);
        assertEquals(REPLY, LettuceRedisScripts.of(connection, timeout, 1).eval("sha", "script", List.of("{ns/a}"), List.of("1")));
        return waits;
    }

    private static final class Done<T> extends CompletableFuture<T> implements RedisFuture<T> {
        @Override
        public String getError() {
            return null;
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) {
            return true;
        }
    }

    @Test
    void aSubMillisecondTimeoutNeverBecomesWaitForever() {
        assertEquals(List.of(1L), waitTimeouts(Duration.ofNanos(1)));
        assertEquals(List.of(1L), waitTimeouts(Duration.ofNanos(500_000)));
    }

    @Test
    void aFractionalMillisecondIsRoundedUp() {
        assertEquals(List.of(2L), waitTimeouts(Duration.ofNanos(1_500_000)));
        assertEquals(List.of(5001L), waitTimeouts(Duration.ofSeconds(5).plusNanos(1)));
    }

    @Test
    void wholeMillisecondsAreUnchanged() {
        assertEquals(List.of(1L), waitTimeouts(Duration.ofMillis(1)));
        assertEquals(List.of(5000L), waitTimeouts(Duration.ofSeconds(5)));
    }
}
