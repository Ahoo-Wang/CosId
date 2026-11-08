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

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * {@link RedisScripts} on a Lettuce connection, standalone or Redis Cluster (06-stores.md §2.2).
 *
 * <p>Each call waits at most the timeout for its reply. With {@code replicas > 0} it then runs
 * {@code WAIT replicas timeout} on the same connection (in a cluster: the connection to the node of the keys, the one
 * that ran the script) and fails with {@link RedisStoreException} when fewer replicas acknowledged. {@code WAIT}
 * blocks the connection it runs on, so give this class a connection of its own, never a shared multiplexed one: the
 * {@link RedisStoreProvider} does.
 *
 * <p>In a cluster with replicas to wait for, the script runs on the connection to the node of its keys, which bypasses
 * Lettuce's routing: a {@code MOVED} or {@code ASK} reply, which Redis gives instead of running the script, is followed
 * here (#1437). {@code MOVED} refreshes the topology and runs the call on the node it names; {@code ASK} runs it there
 * once, after {@code ASKING}. The script and its {@code WAIT} still share that node's connection. No other failure is
 * retried: whether a write ran is unknown then.
 */
public final class LettuceRedisScripts implements RedisScripts {
    /** The redirects one call follows: a slot moved, then migrating, is two. */
    static final int MAX_REDIRECTS = 2;
    private final Targets targets;
    private final Duration timeout;
    private final int replicas;

    private LettuceRedisScripts(Targets targets, Duration timeout, int replicas) {
        this.targets = targets;
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
        Target target = new Target(connection.async(), connection.async(), false);
        return new LettuceRedisScripts(Targets.fixed(target), timeout, replicas);
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
        return of(connection, timeout, replicas, () -> {
        });
    }

    /**
     * Scripts on a Redis Cluster connection; the keys of a call share a hash tag, so it runs on one node.
     *
     * @param connection      the connection, UTF-8 string codec
     * @param timeout         how long a call waits for each reply, and the {@code WAIT} timeout
     * @param replicas        how many replicas of the node must acknowledge each write; 0 runs no {@code WAIT}
     * @param refreshTopology reloads the cluster topology of the connection, such as {@code RedisClusterClient::refreshPartitions}:
     *                        run when a node answers {@code MOVED}, as Lettuce's own routing would
     * @return the scripts
     */
    public static LettuceRedisScripts of(StatefulRedisClusterConnection<String, String> connection, Duration timeout, int replicas,
                                         Runnable refreshTopology) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(refreshTopology, "refreshTopology");
        if (replicas == 0) {
            // No WAIT: let the cluster connection route the script, and follow MOVED redirects.
            Target routed = new Target(connection.async(), connection.async(), false);
            return new LettuceRedisScripts(Targets.fixed(routed), timeout, replicas);
        }
        // WAIT counts the replicas that acknowledged the writes of the connection it runs on, so the script and its WAIT
        // must share one connection: the one to the node serving the key, taken once per call (B-B1).
        return new LettuceRedisScripts(new Targets() {
            @Override
            public Target of(String key) {
                RedisClusterNode node = connection.getPartitions().getPartitionBySlot(SlotHash.getSlot(key));
                if (node == null) {
                    throw new RedisStoreException("No Redis Cluster node serves the key " + key);
                }
                StatefulRedisConnection<String, String> nodeConnection = connection.getConnection(node.getNodeId());
                return new Target(nodeConnection.async(), nodeConnection.async(), false);
            }

            @Override
            public Target redirected(Redirect redirect) {
                if (!redirect.ask()) {
                    // The slot moved for good: later calls find its node in the refreshed topology.
                    refreshTopology.run();
                }
                StatefulRedisConnection<String, String> nodeConnection = connection.getConnection(redirect.host(), redirect.port());
                return new Target(nodeConnection.async(), nodeConnection.async(), redirect.ask());
            }
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
     * The fewest replicas any primary of the cluster has: what {@code wait=auto} waits for (capped at 1 by the provider).
     * Each primary serving slots is asked for its own: the more of its connected replicas ({@code INFO replication},
     * what {@code WAIT} counts) and the replicas the cluster bus knows ({@code CLUSTER REPLICAS}, connected or not). The
     * cluster topology alone is not enough: on Redis 7.0 it may list replicas in sync as primaries without slots, so
     * {@code auto} would resolve to 0 and never {@code WAIT}. When some primaries have replicas and others none,
     * {@code WAIT} would fail every write of the latter, and skipping it would drop the guarantee of the former, so this
     * fails.
     *
     * @param connection the connection
     * @param timeout    how long to wait for each reply
     * @return the replica count of the primary with the fewest replicas
     * @throws RedisStoreException when no primary serves slots, or some primaries have replicas and others none
     */
    public static int fewestReplicas(StatefulRedisClusterConnection<String, String> connection, Duration timeout) {
        Map<String, Integer> replicas = new TreeMap<>();
        for (RedisClusterNode node : connection.getPartitions()) {
            if (node.is(RedisClusterNode.NodeFlag.UPSTREAM) && !node.getSlots().isEmpty()) {
                String nodeId = node.getNodeId();
                StatefulRedisConnection<String, String> primary = connection.getConnection(nodeId);
                int known = await(primary.async().clusterReplicas(nodeId), timeout).size();
                replicas.put(node.getUri() + " " + nodeId, Math.max(connectedReplicas(primary, timeout), known));
            }
        }
        if (replicas.isEmpty()) {
            throw new RedisStoreException("No Redis Cluster primary serves slots: " + connection.getPartitions());
        }
        int fewest = Collections.min(replicas.values());
        if (fewest == 0 && Collections.max(replicas.values()) > 0) {
            throw new RedisStoreException("Some Redis Cluster primaries have replicas and others none, so wait=auto can neither WAIT nor skip it: "
                + replicas + "; set options.wait to off or to a replica count");
        }
        return fewest;
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
        // Every store script names keys, and they share a hash tag: the first one picks the node.
        Target target = targets.of(keys.get(0));
        List<Object> reply;
        for (int redirects = 0; ; redirects++) {
            try {
                reply = script(target, sha, script, keyArray, argArray);
                break;
            } catch (RedisStoreException e) {
                // MOVED and ASK: Redis did not run the script, so running it where they point is safe.
                Optional<Redirect> redirect = Redirect.of(e.getCause());
                if (redirect.isEmpty() || redirects == MAX_REDIRECTS) {
                    throw e;
                }
                target = targets.redirected(redirect.get());
            }
        }
        if (replicas > 0) {
            long acknowledged = await(target.server().waitForReplication(replicas, timeout.toMillis()), timeout.multipliedBy(2));
            if (acknowledged < replicas) {
                throw new RedisStoreException("The write was acknowledged by " + acknowledged + " of " + replicas + " replicas within " + timeout);
            }
        }
        return reply;
    }

    /** Runs the script by digest, by text on {@code NOSCRIPT}; after {@code ASKING} on a target of an {@code ASK}. */
    private List<Object> script(Target target, String sha, String script, String[] keys, String[] args) {
        try {
            return asking(target, () -> target.scripts().evalsha(sha, ScriptOutputType.MULTI, keys, args));
        } catch (RedisNoScriptException e) {
            return asking(target, () -> target.scripts().eval(script, ScriptOutputType.MULTI, keys, args));
        }
    }

    private List<Object> asking(Target target, Supplier<RedisFuture<List<Object>>> command) {
        // ASKING holds for the next command of the connection: both are sent before either reply is awaited.
        RedisFuture<String> asking = target.asking() ? target.server().asking() : null;
        RedisFuture<List<Object>> reply = command.get();
        if (asking != null) {
            await(asking, timeout);
        }
        return await(reply, timeout);
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

    /**
     * Where one call runs: the script and its {@code WAIT}, on the same connection.
     *
     * @param scripts runs the script
     * @param server  runs {@code WAIT}, and {@code ASKING}
     * @param asking  whether the script runs after {@code ASKING}: the target of an {@code ASK}
     */
    private record Target(RedisScriptingAsyncCommands<String, String> scripts, RedisClusterAsyncCommands<String, String> server, boolean asking) {
    }

    /** The targets of the calls: by the key's slot, and where a redirect points. */
    private interface Targets {
        Target of(String key);

        Target redirected(Redirect redirect);

        static Targets fixed(Target target) {
            return new Targets() {
                @Override
                public Target of(String key) {
                    return target;
                }

                @Override
                public Target redirected(Redirect redirect) {
                    throw new RedisStoreException("Redis redirected a call outside a cluster: " + redirect);
                }
            };
        }
    }

    /**
     * A {@code MOVED} or {@code ASK} reply: the node that serves the slot ({@code MOVED slot host:port}), or serves it
     * for this one command while the slot migrates ({@code ASK slot host:port}).
     *
     * @param ask  whether it is an {@code ASK}
     * @param host the node's host
     * @param port the node's port
     */
    record Redirect(boolean ask, String host, int port) {
        static Optional<Redirect> of(Throwable error) {
            if (!(error instanceof RedisCommandExecutionException) || error.getMessage() == null) {
                return Optional.empty();
            }
            String[] reply = error.getMessage().trim().split(" ");
            if (reply.length != 3 || !("MOVED".equals(reply[0]) || "ASK".equals(reply[0]))) {
                return Optional.empty();
            }
            int colon = reply[2].lastIndexOf(':');
            if (colon <= 0) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Redirect("ASK".equals(reply[0]), reply[2].substring(0, colon), Integer.parseInt(reply[2].substring(colon + 1))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
