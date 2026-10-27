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

import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.jdbc.JdbcMachineIdInitializer;
import me.ahoo.cosid.jdbc.JdbcMachineLeaseStore;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;

import com.zaxxer.hikari.HikariDataSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
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
 * P10: the store primitives on a real MySQL ({@link MySql}), one thread; average and sampled latency.
 * {@code reserve} is {@code JdbcIdSegmentDistributor.nextMaxId(100)}; {@code acquire} is
 * {@code MachineLeaseManager.acquire} on {@code JdbcMachineLeaseStore} by a new instance, released after each call
 * (outside the measurement); {@code renew} renews one lease.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MySqlStoreBenchmark {
    static final int MACHINE_BIT = 10;
    static final Duration SAFE_GUARD = Duration.ofSeconds(30);
    static final long STEP = 100;

    HikariDataSource dataSource;
    JdbcIdSegmentDistributor distributor;
    MachineLeaseManager leases;
    String namespace;
    Lease renewing;

    @Setup
    public void setup() {
        dataSource = MySql.dataSource();
        JdbcIdSegmentInitializer initializer = new JdbcIdSegmentInitializer(dataSource);
        initializer.tryInitCosIdTable();
        distributor = new JdbcIdSegmentDistributor("bench", "p10-" + UUID.randomUUID(), STEP, dataSource);
        initializer.tryInitIdSegment(distributor.getNamespacedName(), 0);
        new JdbcMachineIdInitializer(dataSource).tryInitCosIdMachineTable();
        leases = new MachineLeaseManager(new JdbcMachineLeaseStore(dataSource));
        namespace = "p10-" + UUID.randomUUID();
        renewing = leases.acquire(namespace, InstanceId.of("renewer", false), MACHINE_BIT, SAFE_GUARD);
    }

    @TearDown
    public void tearDown() {
        dataSource.close();
    }

    /** The lease of the last {@code acquire}, released after each call. */
    @State(Scope.Thread)
    public static class Acquiring {
        int next;
        Lease lease;

        @TearDown(Level.Invocation)
        public void release(MySqlStoreBenchmark benchmark) {
            benchmark.leases.release(lease, lease.getWatermark());
        }
    }

    @Benchmark
    @Threads(1)
    public long reserve_t1() {
        return distributor.nextMaxId(STEP);
    }

    @Benchmark
    @Threads(1)
    public Lease acquire_t1(Acquiring acquiring) {
        acquiring.lease = leases.acquire(namespace, InstanceId.of("host-" + acquiring.next++, false), MACHINE_BIT, SAFE_GUARD);
        return acquiring.lease;
    }

    @Benchmark
    @Threads(1)
    public Lease renew_t1() {
        renewing = leases.renew(renewing).orElseThrow();
        return renewing;
    }
}
