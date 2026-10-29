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


package me.ahoo.cosid.store.tck;

import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.HostResolver;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.PartsCodecSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** Runtimes of every strategy on {@link InMemoryStore}s, for the tests of the integrations and other runtime users. */
public final class TestRuntimes {
    /** Snowflake, decimal. */
    public static final String DEFAULT = "default";
    /** Segment, radix 36 with the prefix {@code O}. */
    public static final String ORDER = "order";
    /** 80-bit CosId, radix 62. */
    public static final String TOKEN = "token";
    /** Periodic segment, {@code yyyyMMdd} + 6 digits. */
    public static final String BILL = "bill";
    /** Disabled. */
    public static final String OFF = "off";

    private TestRuntimes() {
    }

    /**
     * A runtime of every strategy.
     *
     * @param namespace the namespace
     * @return the started runtime
     */
    public static CosIdRuntime start(String namespace) {
        return CosIdRuntime.start(spec(namespace), options());
    }

    public static RuntimeSpec spec(String namespace) {
        return RuntimeSpec.builder(namespace)
            .store("mem", StoreSpec.builder("memory").timeout(Duration.ofSeconds(1)).build())
            .machine(MachineSpec.Static.builder(1).bits(10).ahead(Duration.ofSeconds(10)).build())
            .generator(DEFAULT, GeneratorSpec.Snowflake.builder()
                .layout(Layout.SNOWFLAKE_MS)
                .regression(ClockRegressionPolicy.DEFAULT)
                .codec(List.of())
                .build())
            .generator(ORDER, GeneratorSpec.Segment.builder("mem")
                .batch(BatchPolicy.DEFAULT)
                .codec(List.of(new CodecSpec.Radix(36, 13, false), new CodecSpec.Prefix("O")))
                .build())
            .generator(TOKEN, GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Radix(62)).build())
            .generator(BILL, GeneratorSpec.PeriodicSegment.builder("mem", Period.YEAR_MONTH_DAY, ZoneId.of("Asia/Shanghai"))
                .batch(BatchPolicy.DEFAULT)
                .periodic(PeriodicPolicy.DEFAULT)
                .codec(new GroupedCodecSpec.DatePrefix("", List.of(new CodecSpec.Radix(10, 6, true))))
                .build())
            .generator(OFF, new GeneratorSpec.Disabled(GeneratorSpec.Segment.builder("mem").batch(BatchPolicy.DEFAULT).codec(List.of()).build()))
            .build();
    }

    public static RuntimeOptions options() {
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
        return RuntimeOptions.builder()
            .time(TimeSource.SYSTEM)
            .listener(me.ahoo.cosid.observe.GeneratorListener.NOOP)
            .storeProvider(memory)
            .hostResolver(HostResolver.LOCAL)
            .codecs(reference -> Optional.empty())
            .build();
    }
}
