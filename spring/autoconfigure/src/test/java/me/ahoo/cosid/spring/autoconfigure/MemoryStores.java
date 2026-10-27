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

package me.ahoo.cosid.spring.autoconfigure;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.tck.FaultInjector;
import me.ahoo.cosid.store.tck.FaultyRangeStore;
import me.ahoo.cosid.store.tck.FaultySlotLeaseStore;
import me.ahoo.cosid.store.tck.InMemoryStore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link StoreProvider} bean of type {@code memory}: {@link InMemoryStore}s, recording opens and closes in
 * {@link #events}.
 */
final class MemoryStores implements StoreProvider {
    static final String TYPE = "memory";

    final List<String> events;
    final List<StoreConfig> configs = new CopyOnWriteArrayList<>();
    /** The TCK's fault injection on every store opened, when set. */
    volatile FaultInjector faults;

    MemoryStores() {
        this(new CopyOnWriteArrayList<>());
    }

    MemoryStores(List<String> events) {
        this.events = events;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Store open(StoreConfig config) {
        configs.add(config);
        events.add("open " + config.name());
        InMemoryStore store = new InMemoryStore();
        return new Store() {
            @Override
            public RangeStore ranges() {
                return faults == null ? store : new FaultyRangeStore(store, faults);
            }

            @Override
            public SlotLeaseStore slots() {
                return faults == null ? store : new FaultySlotLeaseStore(store, faults);
            }

            @Override
            public void close() {
                events.add("close " + config.name());
                store.close();
            }
        };
    }
}
