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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributor;
import me.ahoo.cosid.zookeeper.ZookeeperMachineLeaseStore;

import org.apache.curator.retry.ExponentialBackoffRetry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P10 (ZooKeeper): the store primitives on 4.1.0 {@code cosid-zookeeper}, average and sampled latency, one thread.
 *
 * <ul>
 *     <li>{@code reserve}: {@code ZookeeperIdSegmentDistributor.nextMaxId(100)}, without a call timeout (its fastest
 *     path);</li>
 *     <li>{@code renew}: {@code MachineLeaseManager.renew} of a held lease on {@code ZookeeperMachineLeaseStore}
 *     (which requires a call timeout: 5s, as the starter's default);</li>
 *     <li>{@code acquire}: {@code MachineLeaseManager.acquire} then {@code release}, machine bit 10, nothing else
 *     held;</li>
 *     <li>{@code acquire1024}: the same with the 1023 other machine ids of the namespace held.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ZookeeperStoreBenchmark {
    static final long STEP = 100;
    static final int MACHINE_BIT = 10;
    static final Duration SAFE_GUARD = Duration.ofSeconds(30);

    ZookeeperBench zookeeper;
    ZookeeperIdSegmentDistributor distributor;
    MachineLeaseManager leases;
    Lease held;
    String acquireNamespace;
    InstanceId instance;

    @Setup
    public void setup() throws Exception {
        zookeeper = ZookeeperBench.start();
        distributor = new ZookeeperIdSegmentDistributor("p10-" + UUID.randomUUID(), "reserve", 0, STEP, zookeeper.client,
            new ExponentialBackoffRetry(100, 5, 500), CallTimeout.NONE);
        leases = new MachineLeaseManager(new ZookeeperMachineLeaseStore(zookeeper.client, CallTimeout.of(Duration.ofSeconds(5))));
        held = leases.acquire("p10-renew-" + UUID.randomUUID(), new InstanceId("host-renew", false), MACHINE_BIT, SAFE_GUARD);
        acquireNamespace = "p10-acquire-" + UUID.randomUUID();
        instance = new InstanceId("host-acquire", false);
    }

    @TearDown
    public void tearDown() throws Exception {
        zookeeper.close();
    }

    @Benchmark
    @Threads(1)
    public long reserve_t1() {
        return distributor.nextMaxId(STEP);
    }

    @Benchmark
    @Threads(1)
    public Lease renew_t1() {
        held = leases.renew(held).orElseThrow();
        return held;
    }

    @Benchmark
    @Threads(1)
    public boolean acquire_t1() {
        Lease lease = leases.acquire(acquireNamespace, instance, MACHINE_BIT, SAFE_GUARD);
        return leases.release(lease, lease.getWatermark());
    }

    @Benchmark
    @Threads(1)
    public boolean acquire1024_t1(Full full) {
        Lease lease = leases.acquire(full.namespace, instance, MACHINE_BIT, SAFE_GUARD);
        return leases.release(lease, lease.getWatermark());
    }

    /**
     * A namespace of 1024 machine ids with 1023 held by other instances.
     */
    @State(Scope.Benchmark)
    public static class Full {
        String namespace;

        @Setup
        public void setup(ZookeeperStoreBenchmark benchmark) {
            namespace = "p10-full-" + UUID.randomUUID();
            for (int i = 0; i < (1 << MACHINE_BIT) - 1; i++) {
                benchmark.leases.acquire(namespace, new InstanceId("host-" + i, false), MACHINE_BIT, SAFE_GUARD);
            }
        }
    }
}
