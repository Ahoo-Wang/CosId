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
import me.ahoo.cosid.spring.redis.RedisCommandTimeout;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;
import me.ahoo.cosid.spring.redis.SpringRedisMachineLeaseStore;

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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P10 (Redis): the store primitives one call at a time: reserve a range ({@code nextMaxId}), acquire and release a
 * machine slot ({@code MachineLeaseManager.acquire} + {@code release}), renew a lease.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class RedisStoreBenchmark {
    static final int MACHINE_BIT = 10;
    static final Duration SAFE_GUARD = Duration.ofSeconds(60);

    LettuceConnectionFactory factory;
    SpringRedisIdSegmentDistributor distributor;
    MachineLeaseManager leases;
    String namespace;
    Lease held;
    final AtomicLong instances = new AtomicLong();

    @Setup
    public void setup() {
        factory = Redis4x.connectionFactory();
        StringRedisTemplate template = Redis4x.template(factory);
        distributor = new SpringRedisIdSegmentDistributor("bench", "p10-" + UUID.randomUUID(), 0, 100, template);
        leases = new MachineLeaseManager(new SpringRedisMachineLeaseStore(template, RedisCommandTimeout.of(Duration.ofSeconds(10))));
        namespace = "p10-" + UUID.randomUUID();
        held = leases.acquire(namespace, InstanceId.of("held", false), MACHINE_BIT, SAFE_GUARD);
    }

    @TearDown
    public void tearDown() {
        factory.destroy();
    }

    @Benchmark
    @Threads(1)
    public long reserve_t1() {
        return distributor.nextMaxId(100);
    }

    @Benchmark
    @Threads(1)
    public boolean acquireRelease_t1() {
        Lease lease = leases.acquire(namespace, InstanceId.of("host-" + instances.incrementAndGet(), false), MACHINE_BIT, SAFE_GUARD);
        return leases.release(lease, System.currentTimeMillis());
    }

    @Benchmark
    @Threads(1)
    public Object renew_t1() {
        held = leases.renew(held).orElseThrow();
        return held;
    }
}
