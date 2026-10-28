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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.redis.RedisStoreException;
import me.ahoo.cosid.store.redis.RedisStoreProvider;

import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@link SpringDataRedisScripts}: {@code WAIT}, {@code NOSCRIPT}, failures, and which client a factory gives.
 */
class SpringDataRedisScriptsTest {
    static final LettuceConnectionFactory FACTORY = TestRedis.lettuce();

    @AfterAll
    static void destroy() {
        FACTORY.destroy();
    }

    static Store store(SpringDataRedisScripts scripts) {
        return new RedisStoreProvider().open(StoreConfig.builder("spring", RedisStoreProvider.TYPE)
            .timeout(Duration.ofSeconds(1))
            .client(scripts)
            .build());
    }

    @Test
    void runsTheScriptByTextAfterNoScript() {
        try (RedisConnection connection = FACTORY.getConnection()) {
            connection.scriptingCommands().scriptFlush();
        }
        try (Store store = store(new SpringDataRedisScripts(FACTORY, Duration.ofSeconds(1), "auto"))) {
            String key = "noscript-" + UUID.randomUUID();
            assertEquals(0, store.ranges().reserve(key, 10, 0).start());
            assertEquals(10, store.ranges().reserve(key, 10, 0).start());
        }
    }

    @Test
    void failsWhenFewerReplicasAcknowledgeTheWait() {
        try (Store store = store(new SpringDataRedisScripts(FACTORY, Duration.ofMillis(200), "1"))) {
            RedisStoreException failure = assertThrows(RedisStoreException.class, () -> store.ranges().reserve("wait-" + UUID.randomUUID(), 10, 0));
            assertThat(failure.getMessage(), containsString("WAIT 1: only 0 replicas acknowledged"));
        }
    }

    @Test
    void rejectsAnInvalidWait() {
        assertThrows(IllegalArgumentException.class, () -> new SpringDataRedisScripts(FACTORY, Duration.ofSeconds(1), "sometimes"));
        assertThrows(IllegalArgumentException.class, () -> new SpringDataRedisScripts(FACTORY, Duration.ofSeconds(1), "-1"));
        new SpringDataRedisScripts(FACTORY, Duration.ofSeconds(1), " OFF ");
    }

    @Test
    void aLettuceFactoryGivesItsNativeClientAndAnotherOneTheAdapter() {
        StoreSpec spec = StoreSpec.builder("redis").timeout(Duration.ofSeconds(1)).option("wait", "off").build();
        assertThat(SpringDataRedisScripts.clientOf(FACTORY, spec), instanceOf(RedisClient.class));
        assertThat(SpringDataRedisScripts.clientOf(factory(connection(false, keysAndArgs -> List.of())), spec), instanceOf(SpringDataRedisScripts.class));
        LettuceConnectionFactory unstarted = new LettuceConnectionFactory();
        assertThat(SpringDataRedisScripts.clientOf(unstarted, spec), instanceOf(SpringDataRedisScripts.class));
    }

    @Test
    void aClusterConnectionCannotWait() {
        SpringDataRedisScripts cluster = new SpringDataRedisScripts(factory(connection(true, keysAndArgs -> List.of("1".getBytes(), 2L))),
            Duration.ofSeconds(1), "auto");
        assertThat(assertThrows(RedisStoreException.class, () -> cluster.eval("sha", "script", List.of("k"), List.of("a"))).getMessage(),
            containsString("set wait: off"));
        SpringDataRedisScripts off = new SpringDataRedisScripts(factory(connection(true, keysAndArgs -> List.of("1".getBytes(), 2L))),
            Duration.ofSeconds(1), "off");
        assertEquals(List.of("1", 2L), off.eval("sha", "script", List.of("k"), List.of("a")));
    }

    @Test
    void translatesRedisFailures() {
        SpringDataRedisScripts failing = new SpringDataRedisScripts(factory(connection(false, keysAndArgs -> {
            throw new RedisSystemException("ERR broken", new IllegalStateException("ERR broken"));
        })), Duration.ofSeconds(1), "off");
        assertThat(assertThrows(RedisStoreException.class, () -> failing.eval("sha", "script", List.of("k"), List.of())).getMessage(),
            containsString("ERR broken"));
        SpringDataRedisScripts scalar = new SpringDataRedisScripts(factory(connection(false, keysAndArgs -> 1L)), Duration.ofSeconds(1), "off");
        assertThat(assertThrows(RedisStoreException.class, () -> scalar.eval("sha", "script", List.of("k"), List.of())).getMessage(),
            containsString("no multi-bulk reply"));
    }

    static RedisConnectionFactory factory(RedisConnection connection) {
        return (RedisConnectionFactory) Proxy.newProxyInstance(SpringDataRedisScriptsTest.class.getClassLoader(),
            new Class<?>[] {RedisConnectionFactory.class}, (proxy, method, args) -> "getConnection".equals(method.getName()) ? connection : null);
    }

    static RedisConnection connection(boolean cluster, Function<Object[], Object> evalSha) {
        RedisScriptingCommands scripting = (RedisScriptingCommands) Proxy.newProxyInstance(SpringDataRedisScriptsTest.class.getClassLoader(),
            new Class<?>[] {RedisScriptingCommands.class}, (proxy, method, args) -> evalSha.apply(args));
        Class<?> type = cluster ? RedisClusterConnection.class : RedisConnection.class;
        return (RedisConnection) Proxy.newProxyInstance(SpringDataRedisScriptsTest.class.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> "scriptingCommands".equals(method.getName()) ? scripting : null);
    }
}
