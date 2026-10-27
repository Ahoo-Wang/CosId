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

package me.ahoo.cosid.store.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The ZooKeeper of the tests: {@code COSID_TEST_ZOOKEEPER_CONNECT} when set (the CI job runs a real server), otherwise
 * one in-process curator-test server for the whole test JVM.
 */
final class ZookeeperTests {
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static String connectString;

    private ZookeeperTests() {
    }

    static synchronized String connectString() {
        if (connectString == null) {
            String external = System.getenv("COSID_TEST_ZOOKEEPER_CONNECT");
            if (external != null && !external.isBlank()) {
                connectString = external;
            } else {
                try {
                    TestingServer server = new TestingServer(true);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            server.close();
                        } catch (IOException ignored) {
                            // the JVM is exiting.
                        }
                    }));
                    connectString = server.getConnectString();
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot start the test ZooKeeper", e);
                }
            }
        }
        return connectString;
    }

    /** A new started client, connected; the caller closes it. */
    static CuratorFramework newClient() {
        return newClient(connectString());
    }

    static CuratorFramework newClient(String connectString) {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(connectString)
            .sessionTimeoutMs(30_000).connectionTimeoutMs(10_000)
            .retryPolicy(new ExponentialBackoffRetry(100, 5, 500)).build();
        client.start();
        try {
            if (!client.blockUntilConnected(30, TimeUnit.SECONDS)) {
                client.close();
                throw new UncheckedIOException(new IOException("No connection to " + connectString));
            }
        } catch (InterruptedException e) {
            client.close();
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return client;
    }

    /** A store on a client of its own, as another process would have; closing it closes the client. */
    static ZookeeperStore newStore() {
        return new ZookeeperStore(newClient(), TIMEOUT, true);
    }
}
