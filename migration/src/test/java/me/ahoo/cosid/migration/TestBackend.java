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

import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;

import org.apache.curator.test.TestingServer;
import org.h2.tools.Server;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;

/**
 * A store both a 4.x writer and the migration reach: H2 over TCP and an in-process ZooKeeper always, Redis and MongoDB
 * in Testcontainers when Docker is there (the test is skipped otherwise). One server per kind for the whole JVM; tests
 * keep apart by namespace or database.
 */
enum TestBackend {
    H2("jdbc"),
    ZOOKEEPER("zookeeper"),
    REDIS("redis"),
    MONGO("mongo");

    private final String legacyType;
    private volatile String address;

    TestBackend(String legacyType) {
        this.legacyType = legacyType;
    }

    /** The 4.x store type, as {@code cosid.*.distributor.type} names it. */
    String legacyType() {
        return legacyType;
    }

    /** The address of the server, started on first use; skips the test when it needs Docker that is not there. */
    synchronized String address() {
        if (address == null) {
            address = start();
        }
        return address;
    }

    private String start() {
        switch (this) {
            case H2 -> {
                try {
                    // The 5.0 side's own H2 driver, registered before the 4.x class loader brings its copy.
                    Class.forName("org.h2.Driver");
                    int port;
                    try (ServerSocket socket = new ServerSocket(0)) {
                        port = socket.getLocalPort();
                    }
                    Server server = Server.createTcpServer("-tcpPort", Integer.toString(port), "-ifNotExists").start();
                    return "jdbc:h2:tcp://localhost:" + server.getPort() + "/mem:";
                } catch (SQLException | IOException | ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
            case ZOOKEEPER -> {
                try {
                    TestingServer server = new TestingServer(true);
                    return server.getConnectString();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            case REDIS -> {
                docker();
                GenericContainer<?> redis = new GenericContainer<>(System.getProperty("cosid.test.redis.image", "redis:7.2-alpine")).withExposedPorts(6379);
                redis.start();
                return "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
            }
            default -> {
                docker();
                MongoDBContainer mongo = new MongoDBContainer(System.getProperty("cosid.test.mongo.image", "mongo:7.0")).withReplicaSet();
                mongo.start();
                return mongo.getReplicaSetUrl("cosid_db");
            }
        }
    }

    private static void docker() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            available = false;
        }
        Assumptions.assumeTrue(available, "Docker is not available");
    }

    /** A fresh store of this kind: its own H2 database; the shared server otherwise. */
    Store store() {
        String url = switch (this) {
            case H2 -> address() + "migration-" + UUID.randomUUID().toString().substring(0, 8) + ";DB_CLOSE_DELAY=-1";
            case ZOOKEEPER -> "zookeeper://" + address();
            default -> address();
        };
        return new Store(this, url);
    }

    /**
     * One store: how the 4.x writer, the migration and a 5.0 runtime reach it.
     *
     * @param backend the kind
     * @param url     the URL the migration uses
     */
    record Store(TestBackend backend, String url) {
        Legacy4xWriter writer() {
            return new Legacy4xWriter(backend.legacyType, backend == ZOOKEEPER ? url.substring("zookeeper://".length()) : url);
        }

        StoreLocation location() {
            return StoreLocation.parse(url, backend == H2 ? Map.of("initSchema", "true") : Map.of());
        }

        /** The 5.0 store itself, through its {@code StoreProvider}. */
        me.ahoo.cosid.store.Store open5() {
            StoreSpec spec = spec();
            for (StoreProvider provider : ServiceLoader.load(StoreProvider.class)) {
                if (provider.type().equals(spec.type())) {
                    return provider.open(new StoreConfig("main", spec.type(), spec.timeout(), spec.options(), Optional.empty()));
                }
            }
            throw new IllegalStateException("No store provider of type " + spec.type());
        }

        StoreSpec spec() {
            Map<String, Object> options = switch (backend) {
                case H2 -> Map.of("url", url);
                case ZOOKEEPER -> Map.of("connectString", url.substring("zookeeper://".length()));
                case REDIS -> Map.of("uri", url, "wait", "off");
                default -> Map.of("uri", url);
            };
            return new StoreSpec(backend.legacyType, Duration.ofSeconds(2), options);
        }
    }
}
