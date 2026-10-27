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

import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.tck.RangeStoreContract;

/**
 * The ZooKeeper range store passes the range contract; each instance has a client, so a session, of its own.
 */
class ZookeeperRangeStoreTest extends RangeStoreContract {
    @Override
    protected RangeStore newStore() {
        ZookeeperStore store = ZookeeperTests.newStore();
        return new RangeStore() {
            @Override
            public Range reserve(String key, long size, long initialOffset) {
                return store.ranges().reserve(key, size, initialOffset);
            }

            @Override
            public boolean seal(String key) {
                return store.ranges().seal(key);
            }

            @Override
            public void close() {
                store.close();
            }
        };
    }
}
