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
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.runtime.StoreSpec;

import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The client a {@code redis} store gets from a Spring Data factory, without a Redis server: the factories are never
 * connected, the connections are in-memory proxies.
 */
class SpringDataRedisScriptsClientTest {

    // #1479: the store's options compare without case, - and _, so WAIT is wait for the adapter too.
    @Test
    void theAdapterReadsWaitAsTheStoreReadsItsOptions() {
        assertEquals("off", SpringDataRedisScripts.option(Map.of("WAIT", "off"), "wait", "auto"));
        assertEquals("off", SpringDataRedisScripts.option(Map.of("Wait", "off"), "wait", "auto"));
        assertEquals("2", SpringDataRedisScripts.option(Map.of("_wait_", "2"), "wait", "auto"));
        assertEquals("auto", SpringDataRedisScripts.option(Map.of("waits", "off"), "wait", "auto"));
        for (String key : List.of("wait", "WAIT", "Wait")) {
            StoreSpec spec = StoreSpec.builder("redis").timeout(Duration.ofSeconds(1)).option(key, "off").build();
            Object client = SpringDataRedisScripts.clientOf(clusterFactory(), spec);
            assertThat(client, instanceOf(SpringDataRedisScripts.class));
            // A cluster connection cannot WAIT: only wait off lets the call through.
            assertEquals(List.of("1", 2L), ((SpringDataRedisScripts) client).eval("sha", "script", List.of("k"), List.of("a")), key);
        }
    }

    // #1445: a static master/replica factory's native client has no URI of its own; the adapter keeps its topology.
    @Test
    void aStaticMasterReplicaFactoryGetsTheAdapter() {
        LettuceConnectionFactory masterReplica = new LettuceConnectionFactory(new RedisStaticMasterReplicaConfiguration("127.0.0.1", 6379));
        LettuceConnectionFactory standalone = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 6379));
        try {
            masterReplica.afterPropertiesSet();
            masterReplica.start();
            standalone.afterPropertiesSet();
            standalone.start();
            assertTrue(SpringDataRedisScripts.isStaticMasterReplica(masterReplica));
            assertFalse(SpringDataRedisScripts.isStaticMasterReplica(standalone));
            StoreSpec spec = StoreSpec.builder("redis").timeout(Duration.ofSeconds(1)).build();
            assertThat(SpringDataRedisScripts.clientOf(masterReplica, spec), instanceOf(SpringDataRedisScripts.class));
            assertThat(SpringDataRedisScripts.clientOf(standalone, spec), instanceOf(RedisClient.class));
        } finally {
            masterReplica.destroy();
            standalone.destroy();
        }
    }

    private static RedisConnectionFactory clusterFactory() {
        RedisScriptingCommands scripting = (RedisScriptingCommands) Proxy.newProxyInstance(getClassLoader(),
            new Class<?>[] {RedisScriptingCommands.class}, (proxy, method, args) -> List.of("1".getBytes(), 2L));
        RedisClusterConnection connection = (RedisClusterConnection) Proxy.newProxyInstance(getClassLoader(),
            new Class<?>[] {RedisClusterConnection.class}, (proxy, method, args) -> "scriptingCommands".equals(method.getName()) ? scripting : null);
        return (RedisConnectionFactory) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {RedisConnectionFactory.class},
            (proxy, method, args) -> "getConnection".equals(method.getName()) || "getClusterConnection".equals(method.getName()) ? connection : null);
    }

    private static ClassLoader getClassLoader() {
        return SpringDataRedisScriptsClientTest.class.getClassLoader();
    }
}
