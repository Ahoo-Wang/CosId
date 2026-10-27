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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

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
        return new RuntimeSpec(namespace,
            Map.of("mem", new StoreSpec("memory", Duration.ofSeconds(1), Map.of())),
            Optional.of(new MachineSpec.Static(10, 1, Optional.empty(), Duration.ofSeconds(10), Optional.empty())),
            Map.of(
                DEFAULT, new GeneratorSpec.Snowflake(Layout.SNOWFLAKE_MS, ClockRegressionPolicy.DEFAULT, List.of()),
                ORDER, new GeneratorSpec.Segment("mem", BatchPolicy.DEFAULT,
                    List.of(new CodecSpec.Radix(36, 13, false), new CodecSpec.Prefix("O"))),
                TOKEN, new GeneratorSpec.CosId80(ClockRegressionPolicy.DEFAULT, new PartsCodecSpec.Radix(62)),
                BILL, new GeneratorSpec.PeriodicSegment("mem", Period.YEAR_MONTH_DAY, ZoneId.of("Asia/Shanghai"), BatchPolicy.DEFAULT,
                    PeriodicPolicy.DEFAULT, new GroupedCodecSpec.DatePrefix("", List.of(new CodecSpec.Radix(10, 6, true)))),
                OFF, new GeneratorSpec.Disabled(new GeneratorSpec.Segment("mem", BatchPolicy.DEFAULT, List.of()))));
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
        return new RuntimeOptions(TimeSource.SYSTEM, Optional.empty(), me.ahoo.cosid.observe.GeneratorListener.NOOP, List.of(memory),
            Map.of(), HostResolver.LOCAL, OptionalInt.empty(), reference -> Optional.empty());
    }
}
