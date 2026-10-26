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

import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.store.tck.InMemoryStore;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * P12: filling the {@code @CosId} field of a new entity from a segment generator.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class IdFillerBenchmark {
    /** The reservation step, the same on both sides. */
    static final long STEP = 100;

    CosIdRuntime runtime;
    IdFiller filler;

    @Setup
    public void setup() {
        StoreProvider memory = new StoreProvider() {
            @Override
            public String type() {
                return "memory";
            }

            @Override
            public Store open(StoreConfig config) {
                return new InMemoryStore();
            }
        };
        RuntimeSpec spec = new RuntimeSpec("bench", Map.of("memory", new StoreSpec("memory", Duration.ofSeconds(1), Map.of())),
            Optional.empty(), Map.of(RuntimeSpec.DEFAULT_GENERATOR, new GeneratorSpec.Segment("memory", V5.batch(IdFillerBenchmark.STEP), List.of())));
        RuntimeOptions defaults = RuntimeOptions.defaults();
        RuntimeOptions options = new RuntimeOptions(defaults.time(), Optional.of(V5.EXECUTOR), defaults.listener(), List.of(memory),
            defaults.clients(), defaults.hostResolver(), defaults.port(), defaults.codecs());
        runtime = CosIdRuntime.start(spec, options);
        filler = runtime.filler();
    }

    @TearDown
    public void tearDown() {
        runtime.close();
    }

    @Benchmark
    @Threads(1)
    public Order fill_t1() {
        Order order = new Order();
        filler.fill(order);
        return order;
    }

    /**
     * An entity with a {@code long} id set through its setter.
     */
    public static class Order {
        @CosId
        private long id;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }
}
