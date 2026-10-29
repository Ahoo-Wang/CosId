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
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.mongo.MongoRangeStore;
import me.ahoo.cosid.store.mongo.MongoSlotLeaseStore;

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
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P10 (MongoDB): store primitive latency on a real MongoDB, `COSID_BENCHMARK_MONGODB_URI` (default
 * `mongodb://localhost:27017`). reserve = `MongoRangeStore.reserve(key, 100, 0)`; acquire = `MongoSlotLeaseStore`
 * acquiring and releasing a slot (limit 1024); renew = its `renew` of a held lease.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MongoStoreBenchmark {
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final Duration TTL = Duration.ofSeconds(30);
    static final Duration TOLERANCE = Duration.ofSeconds(1);
    static final int LIMIT = 1 << 10;

    MongoClient client;
    MongoRangeStore ranges;
    MongoSlotLeaseStore slots;
    String key;
    String namespace;
    Owner acquiring;
    SlotLease renewing;

    static String uri() {
        String uri = System.getenv("COSID_BENCHMARK_MONGODB_URI");
        return uri == null || uri.isBlank() ? "mongodb://localhost:27017" : uri;
    }

    @Setup
    public void setup() {
        client = MongoClients.create(uri());
        MongoDatabase database = client.getDatabase("cosid_bench_v5");
        ranges = new MongoRangeStore(database, TIMEOUT);
        slots = new MongoSlotLeaseStore(database, TIMEOUT);
        key = "p10/reserve-" + UUID.randomUUID();
        namespace = "p10-" + UUID.randomUUID();
        acquiring = new Owner("acquiring", UUID.randomUUID());
        renewing = ((AcquireResult.Acquired) slots.acquire(namespace, new Owner("renewing", UUID.randomUUID()), LIMIT, TTL, TOLERANCE,
            0,
            OptionalInt.empty())).lease();
    }

    @TearDown
    public void tearDown() {
        slots.release(renewing, renewing.ceiling());
        client.close();
    }

    @Benchmark
    @Threads(1)
    public Range reserve_t1() {
        return ranges.reserve(key, 100, 0);
    }

    @Benchmark
    @Threads(1)
    public int acquire_t1() {
        SlotLease lease = ((AcquireResult.Acquired) slots.acquire(namespace, acquiring, LIMIT, TTL, TOLERANCE, 0, OptionalInt.empty())).lease();
        slots.release(lease, lease.ceiling());
        return lease.slot();
    }

    @Benchmark
    @Threads(1)
    public SlotLease renew_t1() {
        return slots.renew(renewing, TTL, TOLERANCE).orElseThrow();
    }
}
