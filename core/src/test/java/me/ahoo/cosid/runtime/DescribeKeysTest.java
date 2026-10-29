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

import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The {@code describe()} keys of every strategy (R1-core-17), as {@link GeneratorInfo} lists them: a listed key is
 * never renamed nor removed, and every value is a String, a Number or a Boolean.
 */
class DescribeKeysTest {
    static final Set<String> TIME_BASED = Set.of("epoch", "unit", "timeBits", "nodeBits", "seqBits", "lastIssuedMillis", "slot", "floor",
        "ceiling", "slotValid", "node", "machine", "machineNamespace");
    static final Set<String> SEGMENT = Set.of("key", "step", "maxBatch", "lookahead", "reserveTimeout", "batch", "held");
    static final Set<String> PERIODIC = union(SEGMENT, Set.of("zone", "prewarm", "sealAfter", "period"));

    static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new TreeSet<>(a);
        all.addAll(b);
        return all;
    }

    static RuntimeSpec spec() {
        return RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .machine(leased("db"))
            .generator("snowflake", snowflake(Layout.SNOWFLAKE_MS))
            .generator("cosid80", GeneratorSpec.CosId80.builder().build())
            .generator("segment", segment("db"))
            .generator("periodic", GeneratorSpec.PeriodicSegment.builder("db", Period.YEAR_MONTH_DAY, SHANGHAI).build())
            .generator("custom", GeneratorSpec.Instance.builder(() -> 7).build())
            .generator("off", new GeneratorSpec.Disabled(segment("db")))
            .build();
    }

    static Map<String, GeneratorInfo> describe(boolean count) {
        TestStores db = new TestStores("memory");
        try (CosIdRuntime runtime = CosIdRuntime.start(spec(), options(db).toBuilder().countGenerated(count).build())) {
            runtime.generator("segment").generate();
            runtime.periodic("periodic").generate();
            return runtime.describe();
        }
    }

    @Test
    void everyStrategyHasItsListedKeys() {
        Map<String, GeneratorInfo> infos = describe(false);
        assertThat(new TreeSet<>(infos.get("snowflake").details().keySet()), is(new TreeSet<>(TIME_BASED)));
        assertThat(new TreeSet<>(infos.get("cosid80").details().keySet()), is(new TreeSet<>(TIME_BASED)));
        assertThat(new TreeSet<>(infos.get("segment").details().keySet()), is(new TreeSet<>(SEGMENT)));
        assertThat(new TreeSet<>(infos.get("periodic").details().keySet()), is(new TreeSet<>(PERIODIC)));
        assertThat(infos.get("custom").details().keySet(), is(Set.of("class")));
        assertThat(infos.get("off").details(), is(Map.of("disabled", true)));
        for (GeneratorInfo info : infos.values()) {
            info.details().values().forEach(value -> assertThat(info.name() + ": " + value,
                value instanceof String || value instanceof Number || value instanceof Boolean, is(true)));
        }
    }

    @Test
    void countingAddsGenerated() {
        Map<String, GeneratorInfo> infos = describe(true);
        assertThat(new TreeSet<>(infos.get("segment").details().keySet()), is(union(SEGMENT, Set.of("generated"))));
        assertThat(new TreeSet<>(infos.get("periodic").details().keySet()), is(union(PERIODIC, Set.of("generated"))));
        assertThat(infos.get("custom").details().keySet(), is(Set.of("class", "generated")));
        assertThat(infos.get("segment").details().get("generated"), instanceOf(Long.class));
        assertThat(infos.get("off").details(), is(Map.of("disabled", true)));
    }
}
