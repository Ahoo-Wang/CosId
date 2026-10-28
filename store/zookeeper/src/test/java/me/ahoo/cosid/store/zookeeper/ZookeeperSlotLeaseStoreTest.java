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

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The ZooKeeper slot lease store passes the slot lease contract on the ZooKeeper clock, waiting for real expiries;
 * each instance has a client, so a session, of its own.
 */
class ZookeeperSlotLeaseStoreTest extends SlotLeaseStoreContract {
    @Override
    protected SlotLeaseStore newStore() {
        ZookeeperStore store = ZookeeperTests.newStore();
        return new SlotLeaseStore() {
            @Override
            public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, int slotOffset, OptionalInt preferredSlot) {
                return store.slots().acquire(namespace, owner, limit, ttl, tolerance, slotOffset, preferredSlot);
            }

            @Override
            public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
                return store.slots().renew(lease, ttl, tolerance);
            }

            @Override
            public boolean release(SlotLease lease, long lastIssued) {
                return store.slots().release(lease, lastIssued);
            }

            @Override
            public void close() {
                store.close();
            }
        };
    }
}
