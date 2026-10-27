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
import me.ahoo.cosid.store.Store;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P10 (Redis): the store primitives one call at a time: reserve a range, acquire and release a slot, renew a lease.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class RedisStoreBenchmark {
    static final int LIMIT = 1 << 10;
    static final Duration TTL = Duration.ofSeconds(60);
    static final Duration TOLERANCE = Duration.ofSeconds(1);

    Store store;
    String key;
    String namespace;
    SlotLease held;
    final AtomicLong instances = new AtomicLong();

    @Setup
    public void setup() {
        store = RedisV5.open();
        key = "bench/p10-" + UUID.randomUUID();
        namespace = "p10-" + UUID.randomUUID();
        held = acquire(new Owner("held", UUID.randomUUID()));
    }

    @TearDown
    public void tearDown() {
        store.close();
    }

    private SlotLease acquire(Owner owner) {
        return ((AcquireResult.Acquired) store.slots().acquire(namespace, owner, LIMIT, TTL, TOLERANCE, OptionalInt.empty())).lease();
    }

    @Benchmark
    @Threads(1)
    public long reserve_t1() {
        return store.ranges().reserve(key, 100, 0).end();
    }

    @Benchmark
    @Threads(1)
    public boolean acquireRelease_t1() {
        SlotLease lease = acquire(new Owner("host-" + instances.incrementAndGet(), UUID.randomUUID()));
        return store.slots().release(lease, System.currentTimeMillis());
    }

    @Benchmark
    @Threads(1)
    public Object renew_t1() {
        held = store.slots().renew(held, TTL, TOLERANCE).orElseThrow();
        return held;
    }
}
