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

package me.ahoo.cosid.benchmark;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * The ZooKeeper of P10 (benchmark/scenarios.md), the same on both sides: {@code COSID_BENCH_ZOOKEEPER_CONNECT} when
 * set, otherwise an in-process curator-test server (ZooKeeper 3.9) started for the trial; a Curator client with the
 * 4.x starter's defaults (session 60s, connection 15s, retry 100ms x 5 up to 500ms).
 */
final class ZookeeperBench implements AutoCloseable {
    private final TestingServer server;
    final CuratorFramework client;

    private ZookeeperBench(TestingServer server, CuratorFramework client) {
        this.server = server;
        this.client = client;
    }

    static ZookeeperBench start() throws Exception {
        String external = System.getenv("COSID_BENCH_ZOOKEEPER_CONNECT");
        TestingServer server = external == null || external.isBlank() ? new TestingServer(true) : null;
        CuratorFramework client = CuratorFrameworkFactory.builder()
            .connectString(server == null ? external : server.getConnectString())
            .sessionTimeoutMs(60_000).connectionTimeoutMs(15_000)
            .retryPolicy(new ExponentialBackoffRetry(100, 5, 500)).build();
        client.start();
        if (!client.blockUntilConnected(30, TimeUnit.SECONDS)) {
            client.close();
            if (server != null) {
                server.close();
            }
            throw new IOException("No connection to ZooKeeper");
        }
        return new ZookeeperBench(server, client);
    }

    @Override
    public void close() throws IOException {
        client.close();
        if (server != null) {
            server.close();
        }
    }
}
