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
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

import java.util.ArrayList;
import java.util.List;

/**
 * One Lettuce connection of the migration, standalone or Redis Cluster ({@code cluster=true}), shared by the 4.x
 * reader and the 5.0 target.
 */
public final class RedisConnection implements AutoCloseable {
    private final AutoCloseable client;
    private final AutoCloseable connection;
    private final RedisClusterCommands<String, String> commands;
    private final boolean cluster;

    private RedisConnection(AutoCloseable client, AutoCloseable connection, RedisClusterCommands<String, String> commands, boolean cluster) {
        this.client = client;
        this.connection = connection;
        this.commands = commands;
        this.cluster = cluster;
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
            uri.setCredentialsProvider(new StaticCredentialsProvider(location.option("user"), location.option("password").toCharArray()));
        }
        if (Boolean.parseBoolean(location.option("cluster"))) {
            RedisClusterClient client = RedisClusterClient.create(uri);
            try {
                StatefulRedisClusterConnection<String, String> connection = client.connect();
                return new RedisConnection(client::shutdown, connection, connection.sync(), true);
            } catch (RuntimeException e) {
                client.shutdown();
                throw e;
            }
        }
        RedisClient client = RedisClient.create(uri);
        try {
            StatefulRedisConnection<String, String> connection = client.connect();
            return new RedisConnection(client::shutdown, connection, connection.sync(), false);
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
     * Whether this is a Redis Cluster connection.
     *
     * @return true for a cluster
     */
    public boolean cluster() {
        return cluster;
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
