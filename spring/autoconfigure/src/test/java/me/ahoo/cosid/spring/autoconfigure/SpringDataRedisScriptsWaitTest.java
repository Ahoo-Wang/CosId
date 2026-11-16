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

package me.ahoo.cosid.spring.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link SpringDataRedisScripts}' {@code WAIT} timeout is the store timeout in whole milliseconds, rounded up: Redis
 * reads {@code WAIT n 0} as "wait forever", so a positive timeout below 1 ms must not become 0. The connection is an
 * in-memory double, so no Redis (unlike {@link SpringDataRedisScriptsTest}, whose factory starts one).
 */
class SpringDataRedisScriptsWaitTest {

    /** The {@code WAIT} commands the adapter sent, as {@code "WAIT replicas timeout"}, with {@code wait: 1}. */
    private static List<String> waits(Duration timeout) {
        List<String> waits = new CopyOnWriteArrayList<>();
        ClassLoader loader = SpringDataRedisScriptsWaitTest.class.getClassLoader();
        RedisScriptingCommands scripting = (RedisScriptingCommands) Proxy.newProxyInstance(loader, new Class<?>[] {RedisScriptingCommands.class},
            (proxy, method, args) -> List.of("1".getBytes(StandardCharsets.UTF_8), 2L));
        RedisConnection connection = (RedisConnection) Proxy.newProxyInstance(loader, new Class<?>[] {RedisConnection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "scriptingCommands" -> scripting;
                case "execute" -> {
                    StringBuilder command = new StringBuilder((String) args[0]);
                    for (byte[] arg : (byte[][]) args[1]) {
                        command.append(' ').append(new String(arg, StandardCharsets.US_ASCII));
                    }
                    waits.add(command.toString());
                    yield 1L;
                }
                default -> null;
            });
        RedisConnectionFactory factory = (RedisConnectionFactory) Proxy.newProxyInstance(loader, new Class<?>[] {RedisConnectionFactory.class},
            (proxy, method, args) -> "getConnection".equals(method.getName()) ? connection : null);
        assertEquals(List.of("1", 2L), new SpringDataRedisScripts(factory, timeout, "1").eval("sha", "script", List.of("k"), List.of("a")));
        return waits;
    }

    @Test
    void aSubMillisecondTimeoutNeverBecomesWaitForever() {
        assertEquals(List.of("WAIT 1 1"), waits(Duration.ofNanos(1)));
        assertEquals(List.of("WAIT 1 1"), waits(Duration.ofNanos(500_000)));
    }

    @Test
    void aFractionalMillisecondIsRoundedUp() {
        assertEquals(List.of("WAIT 1 2"), waits(Duration.ofNanos(1_500_000)));
        assertEquals(List.of("WAIT 1 5001"), waits(Duration.ofSeconds(5).plusNanos(1)));
    }

    @Test
    void wholeMillisecondsAreUnchanged() {
        assertEquals(List.of("WAIT 1 1"), waits(Duration.ofMillis(1)));
        assertEquals(List.of("WAIT 1 5000"), waits(Duration.ofSeconds(5)));
    }
}
