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

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.store.zookeeper.ZookeeperStore;

import org.apache.curator.framework.CuratorFramework;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * P10 (ZooKeeper): the store primitives on {@code store/zookeeper}, average and sampled latency, one thread; the same
 * operations as the 4.x side.
 *
 * <ul>
 *     <li>{@code reserve}: {@code RangeStore.reserve(key, 100, 0)};</li>
 *     <li>{@code renew}: {@code SlotLeaseStore.renew} of a held lease, ttl 30s, tolerance 1s;</li>
 *     <li>{@code acquire}: {@code acquire} then {@code release}, limit 1024, nothing else held;</li>
 *     <li>{@code acquire1024}: the same with the 1023 other slots of the namespace held.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ZookeeperStoreBenchmark {
    static final long STEP = 100;
    static final int LIMIT = 1 << 10;
    static final Duration TTL = Duration.ofSeconds(30);
    static final Duration TOLERANCE = Duration.ofSeconds(1);

    ZookeeperBench zookeeper;
    ZookeeperStore store;
    SlotLeaseStore slots;
    String rangeKey;
    SlotLease held;
    String acquireNamespace;
    Owner owner;

    @Setup
    public void setup() throws Exception {
        zookeeper = ZookeeperBench.start();
        store = new ZookeeperStore(zookeeper.client, Duration.ofSeconds(5));
        slots = store.slots();
        rangeKey = "p10/" + UUID.randomUUID() + "/reserve";
        held = acquired(slots, "p10-renew-" + UUID.randomUUID(), new Owner("host-renew", UUID.randomUUID()));
        acquireNamespace = "p10-acquire-" + UUID.randomUUID();
        owner = new Owner("host-acquire", UUID.randomUUID());
    }

    static SlotLease acquired(SlotLeaseStore slots, String namespace, Owner owner) {
        return ((AcquireResult.Acquired) slots.acquire(namespace, owner, LIMIT, TTL, TOLERANCE, OptionalInt.empty())).lease();
    }

    @TearDown
    public void tearDown() throws Exception {
        store.close();
        zookeeper.close();
    }

    @Benchmark
    @Threads(1)
    public long reserve_t1() {
        return store.ranges().reserve(rangeKey, STEP, 0).end();
    }

    @Benchmark
    @Threads(1)
    public SlotLease renew_t1() {
        held = slots.renew(held, TTL, TOLERANCE).orElseThrow();
        return held;
    }

    @Benchmark
    @Threads(1)
    public boolean acquire_t1() {
        SlotLease lease = acquired(slots, acquireNamespace, owner);
        return slots.release(lease, lease.ceiling());
    }

    @Benchmark
    @Threads(1)
    public boolean acquire1024_t1(Full full) {
        SlotLease lease = acquired(slots, full.namespace, owner);
        return slots.release(lease, lease.ceiling());
    }

    /**
     * A namespace of 1024 slots with 1023 held by other owners. The first is acquired through the store; the others
     * are written directly in the node format of {@code SlotState} (06-stores.md §2.4), since 1023 acquires that each
     * read every slot would take most of the trial.
     */
    @State(Scope.Benchmark)
    public static class Full {
        String namespace;

        @Setup
        public void setup(ZookeeperStoreBenchmark benchmark) throws Exception {
            namespace = "p10-full-" + UUID.randomUUID();
            acquired(benchmark.slots, namespace, new Owner("host-0", UUID.randomUUID()));
            CuratorFramework client = benchmark.zookeeper.client;
            List<CompletableFuture<Integer>> creates = new ArrayList<>();
            for (int slot = 1; slot < LIMIT - 1; slot++) {
                String data = "owner=host-" + slot + "\nincarnation=" + UUID.randomUUID() + "\nlastOwner=host-" + slot
                    + "\nfence=1\nttl=600000\ntolerance=1000\nfloor=0\nexpiresMin=0\nceilingMin=0\nnextFloor=0\n";
                CompletableFuture<Integer> created = new CompletableFuture<>();
                client.create().inBackground((c, event) -> created.complete(event.getResultCode()))
                    .forPath("/cosid/slot/" + namespace + "/" + slot, data.getBytes(StandardCharsets.UTF_8));
                creates.add(created);
            }
            for (CompletableFuture<Integer> created : creates) {
                if (created.get() != 0) {
                    throw new IllegalStateException("Could not fill the namespace: " + created.get());
                }
            }
        }
    }
}
