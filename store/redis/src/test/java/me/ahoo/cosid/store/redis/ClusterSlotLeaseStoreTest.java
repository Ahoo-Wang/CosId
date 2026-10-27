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

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.tck.SlotLeaseStoreContract;

import java.util.Map;

/**
 * The slot lease contract on a three-primary Redis Cluster: every script runs on the node of its hash tag.
 */
class ClusterSlotLeaseStoreTest extends SlotLeaseStoreContract {
    @Override
    protected SlotLeaseStore newStore() {
        return RedisServers.open(RedisServers.cluster(), Map.of()).slots();
    }
}
