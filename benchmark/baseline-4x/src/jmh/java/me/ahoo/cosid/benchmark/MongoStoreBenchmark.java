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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.mongo.MongoIdSegmentCollection;
import me.ahoo.cosid.mongo.MongoIdSegmentDistributor;
import me.ahoo.cosid.mongo.MongoIdSegmentInitializer;
import me.ahoo.cosid.mongo.MongoMachineInitializer;
import me.ahoo.cosid.mongo.MongoMachineLeaseStore;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
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
 * P10 (MongoDB): store primitive latency on a real MongoDB, `COSID_BENCHMARK_MONGODB_URI` (default
 * `mongodb://localhost:27017`). reserve = `MongoIdSegmentDistributor.nextMaxId(100)`; acquire = a
 * `LeaseStoreMachineIdDistributor` on `MongoMachineLeaseStore` distributing and reverting a machine id (10 bits);
 * renew = its `guard`.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MongoStoreBenchmark {
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final Duration SAFE_GUARD = Duration.ofSeconds(30);

    MongoClient client;
    MongoIdSegmentDistributor segments;
    LeaseStoreMachineIdDistributor machines;
    String namespace;
    InstanceId acquiring;
    InstanceId renewing;

    static String uri() {
        String uri = System.getenv("COSID_BENCHMARK_MONGODB_URI");
        return uri == null || uri.isBlank() ? "mongodb://localhost:27017" : uri;
    }

    @Setup
    public void setup() {
        client = MongoClients.create(uri());
        MongoDatabase database = client.getDatabase("cosid_bench_4x");
        new MongoIdSegmentInitializer(database).ensureCosIdCollection();
        new MongoMachineInitializer(database).ensureMachineCollection();
        segments = new MongoIdSegmentDistributor("p10", "reserve-" + UUID.randomUUID(), 0, 100,
            new MongoIdSegmentCollection(database.getCollection("cosid")));
        machines = new LeaseStoreMachineIdDistributor(new MongoMachineLeaseStore(database, TIMEOUT), MachineStateStorage.IN_MEMORY,
            ClockBackwardsSynchronizer.DEFAULT);
        namespace = "p10-" + UUID.randomUUID();
        acquiring = InstanceId.of("acquiring-" + UUID.randomUUID(), false);
        renewing = InstanceId.of("renewing-" + UUID.randomUUID(), false);
        machines.distribute(namespace, 10, renewing, SAFE_GUARD);
    }

    @TearDown
    public void tearDown() {
        machines.revert(namespace, renewing);
        client.close();
    }

    @Benchmark
    @Threads(1)
    public long reserve_t1() {
        return segments.nextMaxId(100);
    }

    @Benchmark
    @Threads(1)
    public int acquire_t1() {
        int machineId = machines.distribute(namespace, 10, acquiring, SAFE_GUARD).getMachineId();
        machines.revert(namespace, acquiring);
        return machineId;
    }

    @Benchmark
    @Threads(1)
    public void renew_t1() {
        machines.guard(namespace, renewing, SAFE_GUARD);
    }
}
