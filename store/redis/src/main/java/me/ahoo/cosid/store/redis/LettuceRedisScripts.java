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

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * {@link RedisScripts} on a Lettuce connection, standalone or Redis Cluster (06-stores.md §2.2).
 *
 * <p>Each call waits at most the timeout for its reply. With {@code replicas > 0} it then runs
 * {@code WAIT replicas timeout} on the same connection (in a cluster: the connection to the node of the keys, the one
 * that ran the script) and fails with {@link RedisStoreException} when fewer replicas acknowledged. {@code WAIT}
 * blocks the connection it runs on, so give this class a connection of its own, never a shared multiplexed one: the
 * {@link RedisStoreProvider} does.
 */
public final class LettuceRedisScripts implements RedisScripts {
    private final RedisScriptingAsyncCommands<String, String> commands;
    private final Function<String, BaseRedisAsyncCommands<String, String>> waitTarget;
    private final Duration timeout;
    private final int replicas;

    private LettuceRedisScripts(RedisScriptingAsyncCommands<String, String> commands,
                                Function<String, BaseRedisAsyncCommands<String, String>> waitTarget, Duration timeout, int replicas) {
        this.commands = commands;
        this.waitTarget = waitTarget;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        if (replicas < 0) {
            throw new IllegalArgumentException("replicas must not be negative: " + replicas);
        }
        this.replicas = replicas;
    }

    /**
     * Scripts on a standalone (or primary) connection.
     *
     * @param connection the connection, UTF-8 string codec
     * @param timeout    how long a call waits for each reply, and the {@code WAIT} timeout
     * @param replicas   how many replicas must acknowledge each write; 0 runs no {@code WAIT}
     * @return the scripts
     */
    public static LettuceRedisScripts of(StatefulRedisConnection<String, String> connection, Duration timeout, int replicas) {
        Objects.requireNonNull(connection, "connection");
        return new LettuceRedisScripts(connection.async(), key -> connection.async(), timeout, replicas);
    }

    /**
     * Scripts on a Redis Cluster connection; the keys of a call share a hash tag, so it runs on one node.
     *
     * @param connection the connection, UTF-8 string codec
     * @param timeout    how long a call waits for each reply, and the {@code WAIT} timeout
     * @param replicas   how many replicas of the node must acknowledge each write; 0 runs no {@code WAIT}
     * @return the scripts
     */
    public static LettuceRedisScripts of(StatefulRedisClusterConnection<String, String> connection, Duration timeout, int replicas) {
        Objects.requireNonNull(connection, "connection");
        // Lettuce keeps one write connection per node, shared by the commands routed to its slots: the script's.
        return new LettuceRedisScripts(connection.async(), key -> {
            RedisClusterNode node = connection.getPartitions().getPartitionBySlot(SlotHash.getSlot(key));
            if (node == null) {
                throw new RedisStoreException("No Redis Cluster node serves the key " + key);
            }
            return connection.async().getConnection(node.getNodeId());
        }, timeout, replicas);
    }

    /**
     * The replicas connected to the server, from {@code INFO replication}: what {@code wait=auto} waits for.
     *
     * @param connection the connection
     * @param timeout    how long to wait for the reply
     * @return {@code connected_slaves}
     */
    public static int connectedReplicas(StatefulRedisConnection<String, String> connection, Duration timeout) {
        String info = await(connection.async().info("replication"), timeout);
        for (String line : info.split("\\R")) {
            if (line.startsWith("connected_slaves:")) {
                return Integer.parseInt(line.substring("connected_slaves:".length()).trim());
            }
        }
        return 0;
    }

    /**
     * The fewest replicas any primary of the cluster has, from the cluster topology: what {@code wait=auto} waits for
     * (capped at 1 by the provider).
     *
     * @param connection the connection
     * @return the replica count of the primary with the fewest replicas
     */
    public static int connectedReplicas(StatefulRedisClusterConnection<String, String> connection) {
        int fewest = Integer.MAX_VALUE;
        for (RedisClusterNode node : connection.getPartitions()) {
            if (node.is(RedisClusterNode.NodeFlag.UPSTREAM) && !node.getSlots().isEmpty()) {
                int count = 0;
                for (RedisClusterNode other : connection.getPartitions()) {
                    if (node.getNodeId().equals(other.getSlaveOf())) {
                        count++;
                    }
                }
                fewest = Math.min(fewest, count);
            }
        }
        return fewest == Integer.MAX_VALUE ? 0 : fewest;
    }

    /**
     * How many replicas must acknowledge each write.
     *
     * @return the replicas; 0 when no {@code WAIT} runs
     */
    public int replicas() {
        return replicas;
    }

    @Override
    public List<Object> eval(String sha, String script, List<String> keys, List<String> args) {
        String[] keyArray = keys.toArray(String[]::new);
        String[] argArray = args.toArray(String[]::new);
        List<Object> reply;
        try {
            reply = await(commands.<List<Object>>evalsha(sha, ScriptOutputType.MULTI, keyArray, argArray), timeout);
        } catch (RedisNoScriptException e) {
            reply = await(commands.<List<Object>>eval(script, ScriptOutputType.MULTI, keyArray, argArray), timeout);
        }
        if (replicas > 0 && !keys.isEmpty()) {
            long acknowledged = await(waitTarget.apply(keys.get(0)).waitForReplication(replicas, timeout.toMillis()), timeout.multipliedBy(2));
            if (acknowledged < replicas) {
                throw new RedisStoreException("The write was acknowledged by " + acknowledged + " of " + replicas + " replicas within " + timeout);
            }
        }
        return reply;
    }

    private static <T> T await(RedisFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new RedisStoreException("Interrupted waiting for Redis", e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw RedisStoreException.timeout("Redis did not answer within " + timeout, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RedisNoScriptException noScript) {
                throw noScript;
            }
            if (cause instanceof RedisCommandTimeoutException) {
                throw RedisStoreException.timeout("Redis did not answer in time: " + cause.getMessage(), cause);
            }
            throw new RedisStoreException("Redis failed: " + cause.getMessage(), cause);
        } catch (RedisCommandTimeoutException e) {
            throw RedisStoreException.timeout("Redis did not answer in time: " + e.getMessage(), e);
        } catch (RedisException e) {
            throw new RedisStoreException("Redis failed: " + e.getMessage(), e);
        }
    }
}
