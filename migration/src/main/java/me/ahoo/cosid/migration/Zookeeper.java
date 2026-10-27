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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.concurrent.TimeUnit;

/**
 * The Curator client of a {@code zookeeper://} location, shared by the 4.x reader and the 5.0 target. Both use the
 * absolute paths of their versions ({@code /cosid/...}), so the client has no Curator namespace.
 */
public final class Zookeeper {
    private static final String SCHEME = "zookeeper://";

    private Zookeeper() {
    }

    /**
     * Connects to the location and waits for the connection.
     *
     * @param location a {@code zookeeper://} location
     * @return the started client; the caller closes it
     */
    public static CuratorFramework open(StoreLocation location) {
        String connectString = location.url().substring(SCHEME.length());
        CuratorFramework client = CuratorFrameworkFactory.builder()
            .connectString(connectString)
            .retryPolicy(new ExponentialBackoffRetry(100, 5, 500))
            .build();
        client.start();
        try {
            if (!client.blockUntilConnected(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No connection to ZooKeeper " + connectString + " within 15s");
            }
        } catch (InterruptedException e) {
            client.close();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting to ZooKeeper " + connectString, e);
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
        return client;
    }
}
