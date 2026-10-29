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

import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.lease.StaticSlot;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a 5.x runtime builds by default, shared by the scenarios (benchmark/scenarios.md).
 */
final class V5 {
    static final int SLOT = 1;
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private V5() {
    }

    static SlotHandle slot() {
        StaticSlot slot = new StaticSlot(SLOT, 10, Optional.empty(), Duration.ofSeconds(10), Optional.empty(), EXECUTOR, TimeSource.SYSTEM);
        slot.start();
        return slot;
    }

    static TimeBasedGenerator timeBased(String name, Layout layout) {
        return new TimeBasedGenerator(name, layout, slot(), TimeSource.SYSTEM, ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP);
    }

    /**
     * {@code BatchPolicy.DEFAULT} with another step and that step's default {@code maxBatch}, the rest unchanged.
     */
    static BatchPolicy batch(long step) {
        return BatchPolicy.builder().step(step).build();
    }

    /**
     * A runtime with one segment generator {@code name} on a {@link MemoryRangeStore}, so that a scenario measures the
     * production path: the generator the runtime hands out (counted), on the store as the runtime wraps it
     * ({@code ObservedStores}), with the runtime's default listener (R1-core-06).
     */
    static CosIdRuntime segmentRuntime(String name, BatchPolicy policy) {
        StoreProvider memory = new StoreProvider() {
            @Override
            public String type() {
                return "bench-memory";
            }

            @Override
            public Store open(StoreConfig config) {
                MemoryRangeStore ranges = new MemoryRangeStore();
                return new Store() {
                    @Override
                    public RangeStore ranges() {
                        return ranges;
                    }

                    @Override
                    public SlotLeaseStore slots() {
                        throw new UnsupportedOperationException("bench-memory has no slot leases");
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
        RuntimeSpec spec = RuntimeSpec.builder("bench")
            .store("memory", StoreSpec.builder("bench-memory").timeout(Duration.ofSeconds(1)).build())
            .generator(name, GeneratorSpec.Segment.builder("memory").batch(policy).codec(List.of()).build())
            .build();
        return CosIdRuntime.start(spec, RuntimeOptions.builder().executor(EXECUTOR).storeProvider(memory).build());
    }

    /**
     * A runtime with one segment generator {@code name} at {@code step} on the store {@code store}, opened by its
     * provider from {@code client} (null: from its options): the production path of every P7 (D44; the generator the
     * runtime hands out, on the store as the runtime wraps it).
     */
    static CosIdRuntime storeRuntime(String name, StoreSpec store, Object client, long step) {
        RuntimeSpec spec = RuntimeSpec.builder("bench")
            .store("store", store)
            .generator(name, GeneratorSpec.Segment.builder("store").batch(batch(step)).codec(List.of()).build())
            .build();
        RuntimeOptions.Builder options = RuntimeOptions.builder().executor(EXECUTOR);
        if (client != null) {
            options.client("store", client);
        }
        return CosIdRuntime.start(spec, options.build());
    }

    /**
     * An in-memory range store with no simulated latency, like the 4.x {@code Baseline4x.MemoryDistributor}: one
     * atomic add per reservation. It stays rather than {@code store.tck.InMemoryStore}, so the scenarios measure the
     * generator alone, as the 4.x side does.
     */
    static final class MemoryRangeStore implements RangeStore {
        private final ConcurrentHashMap<String, AtomicLong> values = new ConcurrentHashMap<>();

        @Override
        public Range reserve(String key, long size, long initialOffset) {
            AtomicLong value = values.get(key);
            if (value == null) {
                value = values.computeIfAbsent(key, k -> new AtomicLong(initialOffset));
            }
            long end = value.addAndGet(size);
            return new Range(end - size, end);
        }
    }
}
