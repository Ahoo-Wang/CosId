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

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;

import org.apache.curator.framework.CuratorFramework;

import java.time.Duration;
import java.util.Objects;

/**
 * The ZooKeeper store (06-stores.md §2.4): ranges under {@code /cosid/range}, slot leases under {@code /cosid/slot}, on a
 * started {@link CuratorFramework}. Every operation is bounded by the timeout as a whole, since Curator has no
 * per-operation timeout. Closing it closes the client only when the store owns it.
 *
 * <p>Requires ZooKeeper 3.8 or later on the server (D27); 3.9 is the primary target, the version Curator is aligned
 * with. Acquire relies on read-only {@code multi} (ZooKeeper 3.6+).
 */
public final class ZookeeperStore implements Store {
    private final CuratorFramework client;
    private final boolean ownsClient;
    private final ZookeeperRangeStore ranges;
    private final ZookeeperSlotLeaseStore slots;

    /**
     * A store on a client the caller owns and closes; the client must be started.
     *
     * @param client  the Curator client
     * @param timeout the bound of every store operation
     */
    public ZookeeperStore(CuratorFramework client, Duration timeout) {
        this(client, timeout, false);
    }

    ZookeeperStore(CuratorFramework client, Duration timeout, boolean ownsClient) {
        this.client = Objects.requireNonNull(client, "client");
        this.ownsClient = ownsClient;
        Zk zk = new Zk(client, timeout);
        this.ranges = new ZookeeperRangeStore(zk);
        this.slots = new ZookeeperSlotLeaseStore(zk);
    }

    /**
     * The range store; closing it does nothing, close the store instead.
     *
     * @return the range store
     */
    @Override
    public RangeStore ranges() {
        return ranges;
    }

    /**
     * The slot lease store; closing it does nothing, close the store instead.
     *
     * @return the slot lease store
     */
    @Override
    public SlotLeaseStore slots() {
        return slots;
    }

    @Override
    public void close() {
        if (ownsClient) {
            client.close();
        }
    }
}
