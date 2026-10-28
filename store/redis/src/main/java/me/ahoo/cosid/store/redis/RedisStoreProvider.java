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

import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.StringCodec;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Opens {@link RedisStore}s, type {@code redis} (06-stores.md §2.2); registered with {@link java.util.ServiceLoader}.
 *
 * <p>{@link StoreConfig#client()} may hold:
 * <ul>
 *   <li>a {@link RedisScripts}: used as it is, {@code WAIT} included (the Spring Data adapter);</li>
 *   <li>a {@link RedisClient} or {@link RedisClusterClient}: the store opens a connection of its own on it and closes
 *       that connection, not the client;</li>
 *   <li>a {@code StatefulRedisConnection<String, String>} or {@code StatefulRedisClusterConnection<String, String>}:
 *       used for the scripts, and never closed. Since {@code WAIT} must not block a shared connection, this needs
 *       {@code wait} resolving to 0;</li>
 *   <li>nothing: the store creates a client from {@code options.uri} and closes it.</li>
 * </ul>
 *
 * <p>Options: {@code uri} (a Redis URI, without a client), {@code cluster} ({@code true} for a Redis Cluster
 * {@code uri}), {@code wait} ({@code auto}, the default: {@code WAIT 1} after each write when the server has replicas;
 * {@code off}; or the replica count) and {@code sweepBatch} (expired leases one acquire sweeps, default
 * {@value RedisStore#DEFAULT_SWEEP_BATCH}). {@link StoreConfig#timeout()} bounds each command and {@code WAIT}.
 *
 * <p>The uniqueness of the ids rests on acknowledged writes never being lost: see {@link RedisStore}.
 */
public final class RedisStoreProvider implements StoreProvider {
    /** The store type. */
    public static final String TYPE = "redis";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Store open(StoreConfig config) {
        Map<String, Object> options = config.options();
        int sweepBatch = intOption(options, "sweepBatch", RedisStore.DEFAULT_SWEEP_BATCH);
        String wait = String.valueOf(options.getOrDefault("wait", "auto")).trim().toLowerCase(Locale.ROOT);
        Object client = config.client().orElse(null);
        if (client instanceof RedisScripts scripts) {
            return new RedisStore(scripts, sweepBatch, () -> {
            });
        }
        if (client instanceof StatefulRedisConnection<?, ?> connection) {
            StatefulRedisConnection<String, String> shared = (StatefulRedisConnection<String, String>) connection;
            int replicas = replicas(wait, () -> LettuceRedisScripts.connectedReplicas(shared, config.timeout()));
            requireNoWait(config, replicas);
            return new RedisStore(LettuceRedisScripts.of(shared, config.timeout(), 0), sweepBatch, () -> {
            });
        }
        if (client instanceof StatefulRedisClusterConnection<?, ?> connection) {
            StatefulRedisClusterConnection<String, String> shared = (StatefulRedisClusterConnection<String, String>) connection;
            int replicas = replicas(wait, () -> LettuceRedisScripts.fewestReplicas(shared, config.timeout()));
            requireNoWait(config, replicas);
            return new RedisStore(LettuceRedisScripts.of(shared, config.timeout(), 0), sweepBatch, () -> {
            });
        }
        if (client instanceof RedisClient redisClient) {
            return standalone(redisClient, config, wait, sweepBatch, () -> {
            });
        }
        if (client instanceof RedisClusterClient clusterClient) {
            return cluster(clusterClient, config, wait, sweepBatch, () -> {
            });
        }
        if (client != null) {
            throw new IllegalArgumentException("Store [" + config.name() + "]: unsupported Redis client " + client.getClass().getName());
        }
        Object uri = options.get("uri");
        if (uri == null) {
            throw new IllegalArgumentException("Store [" + config.name() + "]: options.uri is required without a client");
        }
        RedisURI redisUri = RedisURI.create(uri.toString());
        if (Boolean.parseBoolean(String.valueOf(options.getOrDefault("cluster", "false")))) {
            RedisClusterClient own = RedisClusterClient.create(redisUri);
            return cluster(own, config, wait, sweepBatch, own::shutdown);
        }
        RedisClient own = RedisClient.create(redisUri);
        return standalone(own, config, wait, sweepBatch, own::shutdown);
    }

    private static Store standalone(RedisClient client, StoreConfig config, String wait, int sweepBatch, Runnable shutdown) {
        return opened(() -> client.connect(StringCodec.UTF8), shutdown, connection -> LettuceRedisScripts.connectedReplicas(connection, config.timeout()),
            (connection, replicas) -> LettuceRedisScripts.of(connection, config.timeout(), replicas), wait, sweepBatch);
    }

    private static Store cluster(RedisClusterClient client, StoreConfig config, String wait, int sweepBatch, Runnable shutdown) {
        return opened(() -> client.connect(StringCodec.UTF8), shutdown, connection -> LettuceRedisScripts.fewestReplicas(connection, config.timeout()),
            (connection, replicas) -> LettuceRedisScripts.of(connection, config.timeout(), replicas), wait, sweepBatch);
    }

    /** A store on a connection of its own: closing the store closes it, then runs {@code shutdown} (of a client of its own). */
    private static <C extends StatefulConnection<String, String>> Store opened(Supplier<C> connect, Runnable shutdown, ToIntFunction<C> connectedReplicas,
                                                                               BiFunction<C, Integer, RedisScripts> scripts, String wait, int sweepBatch) {
        C connection;
        try {
            connection = connect.get();
        } catch (RuntimeException e) {
            shutdown.run();
            throw e;
        }
        try {
            int replicas = replicas(wait, () -> connectedReplicas.applyAsInt(connection));
            return new RedisStore(scripts.apply(connection, replicas), sweepBatch, () -> {
                connection.close();
                shutdown.run();
            });
        } catch (RuntimeException e) {
            connection.close();
            shutdown.run();
            throw e;
        }
    }

    /** {@code auto}: 1 when the server has replicas (06 §2.2); {@code off}: 0; otherwise the count. */
    static int replicas(String wait, IntSupplier connected) {
        return switch (wait) {
            case "auto" -> Math.min(1, connected.getAsInt());
            case "off" -> 0;
            default -> {
                int replicas;
                try {
                    replicas = Integer.parseInt(wait);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("options.wait must be auto, off or a replica count: " + wait, e);
                }
                if (replicas < 0) {
                    throw new IllegalArgumentException("options.wait must not be negative: " + wait);
                }
                yield replicas;
            }
        };
    }

    private static void requireNoWait(StoreConfig config, int replicas) {
        if (replicas > 0) {
            throw new IllegalStateException("Store [" + config.name() + "]: the server has replicas, and WAIT must not block the injected "
                + "connection; inject the RedisClient / RedisClusterClient so the store opens a connection of its own, or set options.wait=off");
        }
    }

    private static int intOption(Map<String, Object> options, String name, int defaultValue) {
        Object value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString().trim());
    }
}
