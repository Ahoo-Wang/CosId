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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.models.partitions.Partitions;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * #1437: in a cluster with replicas to wait for, the script runs on the node connection of its keys, outside Lettuce's
 * routing; a MOVED or ASK reply, given instead of running the script, is followed, and the script and its WAIT share the
 * connection of the node that ran it. Nodes are in-memory doubles: no Redis.
 */
class ClusterRedirectTest {
    private static final List<String> KEYS = List.of("{ns/a}");
    private static final List<Object> REPLY = List.of("reserved", 1L, 2L);

    /** A node: what each command answers, and the commands it got, in order. */
    private static final class Node {
        final String name;
        final int port;
        final Function<String, Object> answers;
        final List<String> commands = new CopyOnWriteArrayList<>();

        Node(String name, int port, Function<String, Object> answers) {
            this.name = name;
            this.port = port;
            this.answers = answers;
        }

        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> connection() {
            RedisAsyncCommands<String, String> async = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {RedisAsyncCommands.class}, (proxy, method, args) -> {
                    commands.add(method.getName());
                    Done<Object> done = new Done<>();
                    Object answer = answers.apply(method.getName());
                    if (answer instanceof Throwable error) {
                        done.completeExceptionally(error);
                    } else {
                        done.complete(answer);
                    }
                    return done;
                });
            return (StatefulRedisConnection<String, String>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {StatefulRedisConnection.class}, (proxy, method, args) -> "async".equals(method.getName()) ? async : null);
        }
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

    /** A cluster whose topology gives every slot to {@code owner}; connections by node id or by port. */
    @SuppressWarnings("unchecked")
    private static StatefulRedisClusterConnection<String, String> cluster(Node owner, Node... others) {
        Partitions partitions = new Partitions();
        partitions.reload(List.of(new RedisClusterNode(RedisURI.create("127.0.0.1", owner.port), owner.name, true, null, 0, 0, 0,
            IntStream.range(0, 16384).boxed().toList(), EnumSet.of(RedisClusterNode.NodeFlag.UPSTREAM))));
        Map<Integer, StatefulRedisConnection<String, String>> byPort = new java.util.HashMap<>();
        byPort.put(owner.port, owner.connection());
        for (Node other : others) {
            byPort.put(other.port, other.connection());
        }
        return (StatefulRedisClusterConnection<String, String>) Proxy.newProxyInstance(ClusterRedirectTest.class.getClassLoader(),
            new Class<?>[] {StatefulRedisClusterConnection.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getPartitions" -> partitions;
                case "getConnection" -> args.length > 1 && args[1] instanceof Integer port ? byPort.get(port) : byPort.get(owner.port);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static Function<String, Object> serving() {
        return command -> switch (command) {
            case "evalsha" -> REPLY;
            case "waitForReplication" -> 1L;
            case "asking" -> "OK";
            default -> throw new UnsupportedOperationException(command);
        };
    }

    private static Function<String, Object> redirecting(String reply) {
        return command -> new RedisCommandExecutionException(reply);
    }

    private static List<Object> eval(LettuceRedisScripts scripts) {
        return scripts.eval("sha", "script", KEYS, List.of("1"));
    }

    @Test
    void aMovedSlotRunsOnItsNewNodeAndRefreshesTheTopology() {
        Node old = new Node("old", 7000, redirecting("MOVED 1234 127.0.0.1:7001"));
        Node moved = new Node("new", 7001, serving());
        AtomicInteger refreshes = new AtomicInteger();
        LettuceRedisScripts scripts = LettuceRedisScripts.of(cluster(old, moved), Duration.ofSeconds(1), 1, refreshes::incrementAndGet);
        assertEquals(REPLY, eval(scripts));
        assertEquals(List.of("evalsha"), old.commands);
        assertEquals(List.of("evalsha", "waitForReplication"), moved.commands, "the script and its WAIT on the node that ran it");
        assertEquals(1, refreshes.get());
    }

    @Test
    void aMigratingSlotRunsOnceOnItsTargetAfterAsking() {
        Node source = new Node("source", 7000, redirecting("ASK 1234 127.0.0.1:7002"));
        Node target = new Node("target", 7002, serving());
        AtomicInteger refreshes = new AtomicInteger();
        LettuceRedisScripts scripts = LettuceRedisScripts.of(cluster(source, target), Duration.ofSeconds(1), 1, refreshes::incrementAndGet);
        assertEquals(REPLY, eval(scripts));
        assertEquals(List.of("asking", "evalsha", "waitForReplication"), target.commands);
        assertEquals(0, refreshes.get(), "ASK moves one command, not the slot");
        // The next call goes to the source again: the slot is still its own.
        target.commands.clear();
        eval(scripts);
        assertEquals(List.of("evalsha", "evalsha"), source.commands);
    }

    @Test
    void otherFailuresAndEndlessRedirectsAreNotRetried() {
        Node broken = new Node("broken", 7000, redirecting("ERR something else"));
        RedisStoreException failed = assertThrows(RedisStoreException.class,
            () -> eval(LettuceRedisScripts.of(cluster(broken), Duration.ofSeconds(1), 1, () -> { })));
        assertThat(failed.getMessage(), containsString("ERR something else"));
        assertEquals(List.of("evalsha"), broken.commands, "a write that may have run is never retried");

        Node ping = new Node("ping", 7000, redirecting("MOVED 1234 127.0.0.1:7001"));
        Node pong = new Node("pong", 7001, redirecting("MOVED 1234 127.0.0.1:7000"));
        RedisStoreException looping = assertThrows(RedisStoreException.class,
            () -> eval(LettuceRedisScripts.of(cluster(ping, pong), Duration.ofSeconds(1), 1, () -> { })));
        assertThat(looping.getMessage(), containsString("MOVED"));
        List<String> all = new ArrayList<>(ping.commands);
        all.addAll(pong.commands);
        assertEquals(LettuceRedisScripts.MAX_REDIRECTS + 1, all.size());
    }

    @Test
    void redirectRepliesAreParsed() {
        assertEquals(Optional.of(new LettuceRedisScripts.Redirect(false, "10.0.0.2", 6380)),
            LettuceRedisScripts.Redirect.of(new RedisCommandExecutionException("MOVED 3999 10.0.0.2:6380")));
        assertEquals(Optional.of(new LettuceRedisScripts.Redirect(true, "redis-2.local", 6381)),
            LettuceRedisScripts.Redirect.of(new RedisCommandExecutionException("ASK 3999 redis-2.local:6381")));
        assertEquals(Optional.of(new LettuceRedisScripts.Redirect(false, "::1", 6380)),
            LettuceRedisScripts.Redirect.of(new RedisCommandExecutionException("MOVED 3999 ::1:6380")));
        assertEquals(Optional.empty(), LettuceRedisScripts.Redirect.of(new RedisCommandExecutionException("ERR MOVED nothing")));
        assertEquals(Optional.empty(), LettuceRedisScripts.Redirect.of(new RedisCommandExecutionException("MOVED 3999 nowhere")));
        assertEquals(Optional.empty(), LettuceRedisScripts.Redirect.of(new IllegalStateException("MOVED 3999 10.0.0.2:6380")));
    }
}
