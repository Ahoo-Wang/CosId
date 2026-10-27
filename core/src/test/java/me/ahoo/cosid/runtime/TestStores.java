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

package me.ahoo.cosid.runtime;

import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.tck.FaultInjector;
import me.ahoo.cosid.store.tck.FaultyRangeStore;
import me.ahoo.cosid.store.tck.FaultySlotLeaseStore;
import me.ahoo.cosid.store.tck.InMemoryStore;
import me.ahoo.cosid.time.TimeSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A provider of in-memory stores, one {@link InMemoryStore} per store name shared by every runtime it opens for, that
 * records each open and close in {@link #events} and fails on demand.
 */
final class TestStores implements StoreProvider {
    final String type;
    final TimeSource storeClock;
    final Map<String, InMemoryStore> backing = new ConcurrentHashMap<>();
    final List<String> events;
    final List<StoreConfig> configs = new CopyOnWriteArrayList<>();
    final Set<String> failOpen = ConcurrentHashMap.newKeySet();
    final Set<String> failClose = ConcurrentHashMap.newKeySet();
    final Set<String> noRanges = ConcurrentHashMap.newKeySet();
    final Set<String> noSlots = ConcurrentHashMap.newKeySet();
    /** The faults of a store's ranges and slots, by store name (06 §3 fault injection). */
    final Map<String, FaultInjector> faults = new ConcurrentHashMap<>();

    TestStores(String type, TimeSource storeClock, List<String> events) {
        this.type = type;
        this.storeClock = storeClock;
        this.events = events;
    }

    TestStores(String type) {
        this(type, TimeSource.SYSTEM, new CopyOnWriteArrayList<>());
    }

    @Override
    public String type() {
        return type;
    }

    InMemoryStore backing(String name) {
        return backing.computeIfAbsent(name, n -> new InMemoryStore(storeClock));
    }

    @Override
    public Store open(StoreConfig config) {
        String name = config.name();
        configs.add(config);
        if (failOpen.contains(name)) {
            events.add("openFailed:" + name);
            throw new IllegalStateException("cannot open " + name);
        }
        events.add("open:" + name);
        InMemoryStore memory = backing(name);
        return new Store() {
            @Override
            public RangeStore ranges() {
                if (noRanges.contains(name)) {
                    throw new UnsupportedOperationException(name + " has no ranges");
                }
                FaultInjector injector = faults.get(name);
                return injector == null ? memory : new FaultyRangeStore(memory, injector);
            }

            @Override
            public SlotLeaseStore slots() {
                if (noSlots.contains(name)) {
                    throw new UnsupportedOperationException(name + " has no slots");
                }
                FaultInjector injector = faults.get(name);
                return injector == null ? memory : new FaultySlotLeaseStore(memory, injector);
            }

            @Override
            public void close() {
                events.add("close:" + name);
                if (failClose.contains(name)) {
                    throw new IllegalStateException("cannot close " + name);
                }
            }
        };
    }
}
