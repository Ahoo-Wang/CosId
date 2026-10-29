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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.source.RedisSource;
import me.ahoo.cosid.migration.target.RedisTarget;

import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class RedisConnectionTest {
    @Test
    void globEscapeMatchesTheLiteralOnly() {
        assertThat(RedisConnection.globEscape("app.order"), is("app.order"));
        assertThat(RedisConnection.globEscape("a*b?c[d]e\\f"), is("a\\*b\\?c\\[d\\]e\\\\f"));
    }

    @Test
    void aWriteWaitsForAReplicaWhenTheNodeHasOne() {
        // R1-mig-02.
        assertThat(RedisConnection.connectedReplicas("# Replication\r\nrole:master\r\nconnected_slaves:2\r\n"), is(2));
        assertThat(RedisConnection.connectedReplicas("# Replication\r\nrole:master\r\n"), is(0));
        List<String> calls = new ArrayList<>();
        assertThat(replicated(calls, "connected_slaves:0", 0L), is(true));
        assertThat(calls, is(List.of("info")));
        calls.clear();
        assertThat(replicated(calls, "connected_slaves:1", 1L), is(true));
        assertThat(calls, is(List.of("info", "waitForReplication 1 5000")));
        IllegalStateException lost = assertThrows(IllegalStateException.class, () -> replicated(new ArrayList<>(), "connected_slaves:1", 0L));
        assertThat(lost.getMessage(), containsString("The write of [k] reached no replica within PT5S"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aClusterWritesOnTheConnectionItWaitsOn() {
        // R2-mig-03: WAIT counts the writes of its own connection; in a cluster the seal, the raise and the slots go over
        // the connection to the primary of the key's slot, the one WAIT runs on, not over the routing connection.
        List<String> calls = new ArrayList<>();
        RedisCommands<String, String> node = (RedisCommands<String, String>) Proxy.newProxyInstance(RedisCommands.class.getClassLoader(),
            new Class<?>[]{RedisCommands.class}, (proxy, method, args) -> {
                calls.add(method.getName());
                return switch (method.getName()) {
                    case "info" -> "connected_slaves:1";
                    case "waitForReplication" -> 1L;
                    case "get" -> "100";
                    case "incrby" -> 100L + (Long) args[1];
                    case "eval" -> List.of(42L, 0L);
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
        RedisClusterCommands<String, String> routing = (RedisClusterCommands<String, String>) Proxy.newProxyInstance(
            RedisClusterCommands.class.getClassLoader(), new Class<?>[]{RedisClusterCommands.class}, (proxy, method, args) -> {
                throw new AssertionError("the routing connection was used for " + method.getName());
            });
        try (RedisTarget target = new RedisTarget(new RedisConnection(() -> { }, () -> { }, routing, true, key -> node))) {
            assertThat(target.raise("app/order", 42).next(), is(42L));
        }
        assertThat(calls, is(List.of("eval", "info", "waitForReplication")));
        calls.clear();
        try (RedisSource source = new RedisSource(new RedisConnection(() -> { }, () -> { }, routing, true, key -> node))) {
            assertThat(source.seal("app", "order", 1000), is(1100L));
        }
        assertThat(calls, is(List.of("get", "incrby", "info", "waitForReplication")));
    }

    @SuppressWarnings("unchecked")
    private static boolean replicated(List<String> calls, String info, long acknowledged) {
        RedisCommands<String, String> node = (RedisCommands<String, String>) Proxy.newProxyInstance(RedisCommands.class.getClassLoader(),
            new Class<?>[]{RedisCommands.class}, (proxy, method, args) -> switch (method.getName()) {
                case "info" -> {
                    calls.add("info");
                    yield info;
                }
                case "waitForReplication" -> {
                    calls.add("waitForReplication " + args[0] + " " + args[1]);
                    yield acknowledged;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        try (RedisConnection redis = new RedisConnection(() -> { }, () -> { }, null, false, key -> node)) {
            redis.replicated("k");
        }
        return true;
    }
}
