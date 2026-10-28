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

package me.ahoo.cosid.runtime;

import static me.ahoo.cosid.runtime.CosIdRuntimeTest.SHANGHAI;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.leased;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.options;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.segment;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.snowflake;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.store;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IntIdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

/**
 * {@link RuntimeOptions#countGenerated()}: every view counts, the concrete generators do not, and nothing is wrapped
 * when it is off.
 */
class CountGeneratedTest {
    static RuntimeSpec spec() {
        return RuntimeSpec.builder("count")
            .store("mem", store("memory"))
            .machine(leased("mem"))
            .generator("default", snowflake(Layout.SNOWFLAKE_MS))
            .generator("order", segment("mem"))
            .generator("token", GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Radix(62)).build())
            .generator("bill", GeneratorSpec.PeriodicSegment.builder("mem", Period.YEAR_MONTH_DAY, SHANGHAI)
                .batch(BatchPolicy.DEFAULT)
                .periodic(PeriodicPolicy.DEFAULT)
                .codec(new GroupedCodecSpec.DatePrefix("-", List.of()))
                .build())
            .generator("off", new GeneratorSpec.Disabled(segment("mem")))
            .build();
    }

    @Test
    void offHandsOutTheGeneratorsThemselves() {
        try (CosIdRuntime runtime = CosIdRuntime.start(spec(), options(new TestStores("memory")))) {
            assertThat(runtime.generated("default"), is(OptionalLong.empty()));
            assertThat(runtime.generator("default"), sameInstance(runtime.get("default", TimeBasedGenerator.class)));
            assertThat(runtime.generator("order"), sameInstance(runtime.get("order", SegmentGenerator.class)));
            assertThat(runtime.get("token", PartsIdGenerator.class), sameInstance(runtime.get("token", TimeBasedGenerator.class)));
            assertThat(runtime.get("bill", GroupedIdGenerator.class), sameInstance(runtime.periodic("bill")));
            assertThat(runtime.describe().get("order").details().containsKey("generated"), is(false));
        }
    }

    @Test
    void onCountsEveryViewButTheConcreteGenerators() {
        RuntimeOptions options = options(new TestStores("memory")).toBuilder().countGenerated(true).build();
        assertThat(options.countGenerated(), is(true));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec(), options)) {
            runtime.generator("default").generate();
            runtime.stringGenerator("default").generate();
            runtime.get("default", IntIdGenerator.class);
            runtime.get("default", PartsIdGenerator.class).nextParts();
            runtime.get("default", TimeBasedGenerator.class).generate();
            assertThat(runtime.generated("default"), is(OptionalLong.of(3)));

            IdGenerator order = runtime.generator("order");
            assertThat(order, not(instanceOf(SegmentGenerator.class)));
            order.generate();
            runtime.get("order", IntIdGenerator.class).generate();
            runtime.filler().fill(new Entity());
            runtime.get("order", SegmentGenerator.class).generate();
            assertThat(runtime.generated("order"), is(OptionalLong.of(3)));
            assertThat(runtime.describe().get("order").details().get("generated"), is(3L));

            runtime.get("token", PartsIdGenerator.class).nextParts();
            runtime.filler().fill(new Token());
            assertThat(runtime.generated("token"), is(OptionalLong.of(2)));

            GroupedIdGenerator bill = runtime.get("bill", GroupedIdGenerator.class);
            assertThat(bill, not(instanceOf(PeriodicSegmentGenerator.class)));
            bill.generate();
            runtime.periodic("bill").generate();
            runtime.filler().fill(new Bill());
            assertThat(runtime.generated("bill"), is(OptionalLong.of(2)));

            assertThat(runtime.generated("off"), is(OptionalLong.empty()));
        }
    }

    static class Entity {
        @CosId("order")
        long id;
    }

    static class Token {
        @CosId("token")
        String id;
    }

    static class Bill {
        @CosId("bill")
        String id;
    }
}
