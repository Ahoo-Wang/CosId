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

import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.store.redis.RedisScripts;
import me.ahoo.cosid.store.redis.RedisStoreException;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.IntegerOutput;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * {@link RedisScripts} on a Spring Data {@link RedisConnectionFactory} (D6), for a factory without a native Lettuce
 * client, such as a Jedis one; a {@link LettuceConnectionFactory}'s own client is given to the store instead
 * ({@link #clientOf}), which opens a connection of its own on it.
 *
 * <p>Each call takes a connection from the factory, runs the script on it (by digest, by text on {@code NOSCRIPT}) and,
 * with replicas to wait for, runs {@code WAIT replicas timeout} on that same connection: the store relies on it
 * (see {@link RedisScripts}). The option {@code wait} is {@code auto} (default: {@code WAIT 1} when the server has
 * replicas), {@code off} or the replica count. On Redis Cluster the connection that ran the script is not reachable for
 * {@code WAIT}, so a cluster factory needs {@code wait: off}. The factory's own command timeout bounds each command;
 * the store timeout bounds {@code WAIT}.
 */
public final class SpringDataRedisScripts implements RedisScripts {
    private final RedisConnectionFactory factory;
    private final Duration timeout;
    private final String wait;
    private volatile int replicas = -1;

    /**
     * An adapter.
     *
     * @param factory the connection factory
     * @param timeout the {@code WAIT} timeout
     * @param wait    {@code auto}, {@code off} or the replica count
     */
    public SpringDataRedisScripts(RedisConnectionFactory factory, Duration timeout, String wait) {
        this.factory = factory;
        this.timeout = timeout;
        this.wait = wait.trim().toLowerCase(Locale.ROOT);
        if (!"auto".equals(this.wait) && !"off".equals(this.wait)) {
            try {
                if (Integer.parseInt(this.wait) < 0) {
                    throw new IllegalArgumentException("wait must not be negative: " + wait);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("wait must be auto, off or a replica count: " + wait, e);
            }
        }
    }

    /**
     * The client a {@code redis} store gets from a connection factory: the native Lettuce client of a started
     * {@link LettuceConnectionFactory}, an adapter otherwise.
     *
     * @param factory the factory
     * @param store   the store
     * @return the client
     */
    static Object clientOf(RedisConnectionFactory factory, StoreSpec store) {
        if (factory instanceof LettuceConnectionFactory lettuce && lettuce.isRunning()) {
            AbstractRedisClient client = lettuce.getNativeClient();
            if (client != null) {
                return client;
            }
        }
        return new SpringDataRedisScripts(factory, store.timeout(), String.valueOf(store.options().getOrDefault("wait", "auto")));
    }

    @Override
    public List<Object> eval(String sha, String script, List<String> keys, List<String> args) {
        byte[][] keysAndArgs = new byte[keys.size() + args.size()][];
        int i = 0;
        for (String key : keys) {
            keysAndArgs[i++] = key.getBytes(StandardCharsets.UTF_8);
        }
        for (String arg : args) {
            keysAndArgs[i++] = arg.getBytes(StandardCharsets.UTF_8);
        }
        try (RedisConnection connection = factory.getConnection()) {
            List<Object> reply = run(connection, sha, script, keys.size(), keysAndArgs);
            int count = replicas(connection);
            if (count > 0) {
                Object acknowledged = waitFor(connection, count);
                long acks = acknowledged instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(acknowledged));
                if (acks < count) {
                    throw new RedisStoreException("WAIT " + count + ": only " + acks + " replicas acknowledged within " + timeout);
                }
            }
            return reply;
        } catch (QueryTimeoutException e) {
            throw RedisStoreException.timeout("Redis did not answer in time: " + e.getMostSpecificCause(), e);
        } catch (DataAccessException e) {
            throw new RedisStoreException("Redis failed: " + e.getMostSpecificCause(), e);
        }
    }

    /** {@code WAIT}; a Lettuce connection needs to be told that the reply is an integer. */
    private Object waitFor(RedisConnection connection, int count) {
        if (connection instanceof LettuceConnection lettuce) {
            return lettuce.execute("WAIT", new IntegerOutput<>(ByteArrayCodec.INSTANCE), bytes(count), bytes(timeout.toMillis()));
        }
        return connection.execute("WAIT", bytes(count), bytes(timeout.toMillis()));
    }

    private static List<Object> run(RedisConnection connection, String sha, String script, int keys, byte[][] keysAndArgs) {
        Object reply;
        try {
            reply = connection.scriptingCommands().evalSha(sha, ReturnType.MULTI, keys, keysAndArgs);
        } catch (DataAccessException e) {
            if (!isNoScript(e)) {
                throw e;
            }
            reply = connection.scriptingCommands().eval(script.getBytes(StandardCharsets.UTF_8), ReturnType.MULTI, keys, keysAndArgs);
        }
        if (!(reply instanceof List<?> items)) {
            throw new RedisStoreException("The script returned no multi-bulk reply: " + reply);
        }
        List<Object> values = new ArrayList<>(items.size());
        for (Object item : items) {
            values.add(item instanceof byte[] text ? new String(text, StandardCharsets.UTF_8) : item);
        }
        return values;
    }

    private static boolean isNoScript(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("NOSCRIPT")) {
                return true;
            }
        }
        return false;
    }

    /** The replicas {@code WAIT} waits for, resolved on first use: {@code auto} reads {@code INFO replication}. */
    private int replicas(RedisConnection connection) {
        int current = replicas;
        if (current >= 0) {
            return current;
        }
        current = switch (wait) {
            case "off" -> 0;
            case "auto" -> Math.min(1, connectedReplicas(connection));
            default -> Integer.parseInt(wait);
        };
        if (current > 0 && connection instanceof RedisClusterConnection) {
            throw new RedisStoreException("WAIT does not reach the node that ran the script through a Spring Data Redis cluster connection:"
                + " give the store a Lettuce RedisClusterClient, or set wait: off");
        }
        replicas = current;
        return current;
    }

    private static int connectedReplicas(RedisConnection connection) {
        if (connection instanceof RedisClusterConnection) {
            // A cluster needs wait: off (see replicas); its topology is not read here.
            return 1;
        }
        Properties info = connection.serverCommands().info("replication");
        String connected = info == null ? null : info.getProperty("connected_slaves");
        return connected == null ? 0 : Integer.parseInt(connected.trim());
    }

    private static byte[] bytes(long value) {
        return Long.toString(value).getBytes(StandardCharsets.US_ASCII);
    }
}
