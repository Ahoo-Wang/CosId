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

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanIterator;
import io.lettuce.core.StaticCredentialsProvider;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One Lettuce connection of the migration, standalone or Redis Cluster ({@code cluster=true}), shared by the 4.x
 * reader and the 5.0 target.
 */
public final class RedisConnection implements AutoCloseable {
    /** How long a write waits for a replica to acknowledge it ({@link #replicated}). */
    public static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private final AutoCloseable client;
    private final AutoCloseable connection;
    private final RedisClusterCommands<String, String> commands;
    private final boolean cluster;
    private final Function<String, RedisCommands<String, String>> nodeOf;

    RedisConnection(AutoCloseable client, AutoCloseable connection, RedisClusterCommands<String, String> commands, boolean cluster,
                            Function<String, RedisCommands<String, String>> nodeOf) {
        this.client = client;
        this.connection = connection;
        this.commands = commands;
        this.cluster = cluster;
        this.nodeOf = nodeOf;
    }

    /**
     * Connects to the location.
     *
     * @param location a {@code redis://} location
     * @return the connection
     */
    public static RedisConnection open(StoreLocation location) {
        RedisURI uri = RedisURI.create(location.url());
        if (location.option("password") != null) {
            uri.setCredentialsProvider(new StaticCredentialsProvider(location.user(), location.option("password").toCharArray()));
        }
        if (Boolean.parseBoolean(location.option("cluster"))) {
            RedisClusterClient client = RedisClusterClient.create(uri);
            try {
                StatefulRedisClusterConnection<String, String> connection = client.connect();
                // A cluster write goes over the connection to the primary of the key's slot, which WAIT must use too.
                return new RedisConnection(client::shutdown, connection, connection.sync(), true,
                    key -> connection.getConnection(connection.getPartitions().getPartitionBySlot(SlotHash.getSlot(key)).getNodeId()).sync());
            } catch (RuntimeException e) {
                client.shutdown();
                throw e;
            }
        }
        RedisClient client = RedisClient.create(uri);
        try {
            StatefulRedisConnection<String, String> connection = client.connect();
            RedisCommands<String, String> sync = connection.sync();
            return new RedisConnection(client::shutdown, connection, sync, false, key -> sync);
        } catch (RuntimeException e) {
            client.shutdown();
            throw e;
        }
    }

    /**
     * The synchronous commands.
     *
     * @return the commands
     */
    public RedisClusterCommands<String, String> commands() {
        return commands;
    }

    /**
     * The synchronous commands of the connection a write of {@code key} goes over, which {@link #replicated} waits on:
     * in a cluster the connection to the primary of the key's slot (R2-mig-03). {@code WAIT} counts the writes of its
     * own connection only, and the routing connection of {@link #commands()} is another one there.
     *
     * @param key the key to write
     * @return the commands
     */
    public RedisClusterCommands<String, String> commandsFor(String key) {
        return nodeOf.apply(key);
    }

    /**
     * Whether this is a Redis Cluster connection.
     *
     * @return true for a cluster
     */
    public boolean cluster() {
        return cluster;
    }

    /**
     * Waits until a replica holds the writes of this connection to the node of {@code key} ({@code WAIT 1}), as the
     * 5.0 runtime does with {@code wait=auto} (R1-mig-02): a seal or a raise that a failover lost would let 5.0 issue
     * values that 4.x issued. Nothing to wait for when the node has no replica.
     *
     * @param key the key just written
     * @throws IllegalStateException if the node has a replica and none acknowledged within {@link #WAIT_TIMEOUT}
     */
    public void replicated(String key) {
        RedisCommands<String, String> node = nodeOf.apply(key);
        if (connectedReplicas(node.info("replication")) == 0) {
            return;
        }
        long acknowledged = node.waitForReplication(1, WAIT_TIMEOUT.toMillis());
        if (acknowledged < 1) {
            throw new IllegalStateException("The write of [" + key + "] reached no replica within " + WAIT_TIMEOUT
                + ": the migration stops, since a failover now would lose it; check the replication and run the step again");
        }
    }

    /**
     * The {@code connected_slaves} of an {@code INFO replication} reply.
     *
     * @param info the reply
     * @return the connected replicas, 0 when the reply has none
     */
    static int connectedReplicas(String info) {
        for (String line : info.split("\\R")) {
            if (line.startsWith("connected_slaves:")) {
                return Integer.parseInt(line.substring("connected_slaves:".length()).trim());
            }
        }
        return 0;
    }

    /**
     * The keys matching a glob pattern, scanned on every node of a cluster.
     *
     * @param pattern the pattern
     * @return the keys
     */
    public List<String> scan(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanIterator<String> iterator = ScanIterator.scan(commands, ScanArgs.Builder.matches(pattern).limit(1000));
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    /**
     * Escapes the glob characters of a literal.
     *
     * @param literal the text
     * @return the text as a pattern matching only itself
     */
    public static String globEscape(String literal) {
        StringBuilder escaped = new StringBuilder(literal.length() + 8);
        for (char c : literal.toCharArray()) {
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (Exception e) {
            // closing anyway
        } finally {
            try {
                client.close();
            } catch (Exception e) {
                // shut down anyway
            }
        }
    }
}
