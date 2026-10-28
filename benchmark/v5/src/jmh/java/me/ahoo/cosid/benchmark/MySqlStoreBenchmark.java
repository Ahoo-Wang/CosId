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
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.jdbc.JdbcStore;

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
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P10: the store primitives on a real MySQL ({@link MySql}), one thread; average and sampled latency.
 * {@code reserve} of 100 values; {@code acquire} in a namespace of 1024 slots by a new owner, released after each call
 * (outside the measurement); {@code renew} renews one lease.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MySqlStoreBenchmark {
    static final int LIMIT = 1 << 10;
    static final Duration TTL = Duration.ofSeconds(30);
    static final Duration TOLERANCE = Duration.ofSeconds(1);
    static final long STEP = 100;

    HikariDataSource dataSource;
    RangeStore ranges;
    SlotLeaseStore slots;
    String key;
    String namespace;
    SlotLease renewing;

    @Setup
    public void setup() {
        dataSource = MySql.dataSource();
        JdbcStore store = JdbcStore.of(dataSource, Duration.ofSeconds(3));
        store.initSchema();
        ranges = store.ranges();
        slots = store.slots();
        key = "bench/p10-" + UUID.randomUUID();
        namespace = "p10-" + UUID.randomUUID();
        renewing = acquire(new Owner("renewer", UUID.randomUUID()));
    }

    SlotLease acquire(Owner owner) {
        return ((AcquireResult.Acquired) slots.acquire(namespace, owner, LIMIT, TTL, TOLERANCE, 0, OptionalInt.empty())).lease();
    }

    @TearDown
    public void tearDown() {
        dataSource.close();
    }

    /** The lease of the last {@code acquire}, released after each call. */
    @State(Scope.Thread)
    public static class Acquiring {
        int next;
        SlotLease lease;

        @TearDown(Level.Invocation)
        public void release(MySqlStoreBenchmark benchmark) {
            benchmark.slots.release(lease, lease.floor());
        }
    }

    @Benchmark
    @Threads(1)
    public Range reserve_t1() {
        return ranges.reserve(key, STEP, 0);
    }

    @Benchmark
    @Threads(1)
    public SlotLease acquire_t1(Acquiring acquiring) {
        acquiring.lease = acquire(new Owner("host-" + acquiring.next++, UUID.randomUUID()));
        return acquiring.lease;
    }

    @Benchmark
    @Threads(1)
    public SlotLease renew_t1() {
        renewing = slots.renew(renewing, TTL, TOLERANCE).orElseThrow();
        return renewing;
    }
}
