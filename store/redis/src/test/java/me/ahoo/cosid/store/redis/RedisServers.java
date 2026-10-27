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

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DnsResolvers;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Redis servers of the tests, started once per test JVM and left to Testcontainers to remove: standalone
 * ({@code COSID_TEST_REDIS_URI} when set, AGENTS.md), a primary with one replica, and a three-primary Redis Cluster.
 */
final class RedisServers {
    static final DockerImageName IMAGE = DockerImageName.parse(System.getProperty("cosid.test.redis.image", "redis:8.10-alpine"));
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int PORT = 6379;
    private static final List<Integer> CLUSTER_PORTS = List.of(7000, 7001, 7002);

    private static String standaloneUri;
    private static RedisClient standalone;
    private static RedisClusterClient cluster;
    private static Replicated replicated;

    private RedisServers() {
    }

    /** The URI of the standalone server. */
    static synchronized String standaloneUri() {
        if (standaloneUri == null) {
            String uri = System.getenv("COSID_TEST_REDIS_URI");
            if (uri == null || uri.isBlank()) {
                GenericContainer<?> redis = new GenericContainer<>(IMAGE).withExposedPorts(PORT);
                redis.start();
                uri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(PORT);
            }
            standaloneUri = uri;
        }
        return standaloneUri;
    }

    /** A client on the standalone server, shared by the tests. */
    static synchronized RedisClient standalone() {
        if (standalone == null) {
            standalone = RedisClient.create(RedisURI.create(standaloneUri()));
        }
        return standalone;
    }

    /** A client on a three-primary cluster in one container, its node addresses mapped to the published ports. */
    static synchronized RedisClusterClient cluster() {
        if (cluster == null) {
            StringBuilder script = new StringBuilder();
            for (int port : CLUSTER_PORTS) {
                script.append("redis-server --port ").append(port).append(" --cluster-enabled yes --cluster-config-file nodes-").append(port)
                    .append(".conf --save '' --appendonly no --daemonize yes && ");
            }
            script.append("sleep 1 && redis-cli --cluster create");
            CLUSTER_PORTS.forEach(port -> script.append(" 127.0.0.1:").append(port));
            script.append(" --cluster-replicas 0 --cluster-yes && tail -f /dev/null");
            GenericContainer<?> redis = new GenericContainer<>(IMAGE)
                .withExposedPorts(CLUSTER_PORTS.toArray(Integer[]::new))
                .withCommand("sh", "-c", script.toString())
                .waitingFor(Wait.forLogMessage(".*All 16384 slots covered.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
            redis.start();
            String host = redis.getHost();
            Map<Integer, Integer> mapped = new HashMap<>();
            CLUSTER_PORTS.forEach(port -> mapped.put(port, redis.getMappedPort(port)));
            MappingSocketAddressResolver resolver = MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED, address ->
                mapped.containsKey(address.getPort()) ? HostAndPort.of(host, mapped.get(address.getPort())) : address);
            ClientResources resources = ClientResources.builder().socketAddressResolver(resolver).build();
            cluster = RedisClusterClient.create(resources, RedisURI.create("redis://127.0.0.1:" + CLUSTER_PORTS.get(0)));
        }
        return cluster;
    }

    /** A primary with one replica shared by the contract tests, which never break it. */
    static synchronized Replicated sharedReplicated() {
        if (replicated == null) {
            replicated = replicated();
        }
        return replicated;
    }

    /**
     * A new primary with one replica, connected and in sync. Each call starts its own pair, so a test may break it.
     *
     * @return the pair
     */
    static Replicated replicated() {
        Network network = Network.newNetwork();
        GenericContainer<?> primary = new GenericContainer<>(IMAGE).withExposedPorts(PORT).withNetwork(network).withNetworkAliases("primary")
            .withCommand("redis-server", "--repl-diskless-sync-delay", "0");
        primary.start();
        GenericContainer<?> replica = new GenericContainer<>(IMAGE).withExposedPorts(PORT).withNetwork(network)
            .withCommand("redis-server", "--replicaof", "primary", String.valueOf(PORT));
        replica.start();
        Replicated replicated = new Replicated(primary, replica,
            RedisClient.create(RedisURI.create("redis://" + primary.getHost() + ":" + primary.getMappedPort(PORT))),
            RedisClient.create(RedisURI.create("redis://" + replica.getHost() + ":" + replica.getMappedPort(PORT))));
        replicated.awaitInSync();
        return replicated;
    }

    static Store open(Object client, Map<String, Object> options) {
        return new RedisStoreProvider().open(new StoreConfig("test", RedisStoreProvider.TYPE, TIMEOUT, options, Optional.of(client)));
    }

    /** A primary with one replica. */
    record Replicated(GenericContainer<?> primaryContainer, GenericContainer<?> replicaContainer, RedisClient primary, RedisClient replica) {
        /** Waits until the replica is online: connected and through its first sync, so it acknowledges writes. */
        void awaitInSync() {
            long deadline = System.nanoTime() + Duration.ofMinutes(1).toNanos();
            try (var connection = primary.connect()) {
                while (!connection.sync().info("replication").contains("state=online")) {
                    if (System.nanoTime() > deadline) {
                        throw new AssertionError("The replica did not connect");
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
