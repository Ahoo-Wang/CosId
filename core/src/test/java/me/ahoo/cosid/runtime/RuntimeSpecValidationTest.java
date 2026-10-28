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

import static me.ahoo.cosid.runtime.CosIdRuntimeTest.leased;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.options;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.segment;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.snowflake;
import static me.ahoo.cosid.runtime.CosIdRuntimeTest.store;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

class RuntimeSpecValidationTest {
    final TestStores memory = new TestStores("memory");

    String problems(RuntimeSpec spec) {
        return problems(spec, options(memory));
    }

    String problems(RuntimeSpec spec, RuntimeOptions options) {
        CosIdException invalid = assertThrows(CosIdException.class, () -> CosIdRuntime.start(spec, options));
        // Nothing is opened before the whole spec is valid.
        assertThat(memory.events, is(empty()));
        return invalid.getMessage();
    }

    @Test
    void timeBasedGeneratorsNeedAMachine() {
        String message = problems(RuntimeSpec.builder("app")
            .generator("default", snowflake(Layout.SNOWFLAKE_MS))
            .generator("token", GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Radix(62)).build())
            .build());
        assertThat(message, containsString("Invalid RuntimeSpec of namespace [app]"));
        assertThat(message, containsString("time-based generators [default, token] need a machine slot, but the spec has no machine"));
    }

    @Test
    void layoutsMustCoverTheMachineBits() {
        String message = problems(RuntimeSpec.builder("app")
            .store("m", store("memory"))
            .machine(leased("m"))
            .generator("js", snowflake(Layout.JS_SAFE_MS))
            .generator("wide", snowflake(Layout.COSID_80))
            .build());
        assertThat(message, containsString("generator [js]: the layout has 3 node bits, fewer than machine.bits 10"));
        assertThat(message, containsString("generator [wide]: a snowflake layout must fit a long, not 80 bits: use cosid80"));
    }

    @Test
    void storesMustExistAndHaveAProvider() {
        String message = problems(RuntimeSpec.builder("app")
            .store("odd", store("unknown-type"))
            .machine(leased("nowhere"))
            .generator("default", snowflake(Layout.SNOWFLAKE_MS))
            .generator("order", segment("missing"))
            .generator("bill", segment("odd"))
            .build());
        assertThat(message, containsString("machine: no store [nowhere]"));
        assertThat(message, containsString("generator [order]: no store [missing]"));
        assertThat(message, containsString("store [odd]: no StoreProvider of type [unknown-type]"));
    }

    @Test
    void storeTimeoutMustNotExceedTheReserveTimeout() {
        BatchPolicy quick = BatchPolicy.builder()
            .step(100)
            .initialOffset(0)
            .lookahead(Duration.ofSeconds(1))
            .maxBatch(100_000)
            .reserveTimeout(Duration.ofMillis(500))
            .build();
        String message = problems(RuntimeSpec.builder("app")
            .store("db", StoreSpec.builder("memory").timeout(Duration.ofSeconds(1)).build())
            .store("zero", StoreSpec.builder("memory").timeout(Duration.ZERO).build())
            .generator("order", GeneratorSpec.Segment.builder("db").batch(quick).codec(List.of()).build())
            .generator("bill", GeneratorSpec.PeriodicSegment.builder("db", Period.YEAR, ZoneOffset.UTC)
                .batch(quick)
                .periodic(PeriodicPolicy.DEFAULT)
                .codec(new GroupedCodecSpec.DatePrefix("-", List.of()))
                .build())
            .generator("fine", segment("zero"))
            .build());
        assertThat(message, containsString("generator [order]: store [db] timeout PT1S exceeds batch.reserveTimeout PT0.5S"));
        assertThat(message, containsString("generator [bill]: store [db] timeout PT1S exceeds batch.reserveTimeout PT0.5S"));
        assertThat(message, containsString("store [zero]: timeout must be positive: PT0S"));
    }

    @Test
    void codecListsAreChecked() {
        String message = problems(RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .generator("friendly", segment("db", new CodecSpec.Friendly(ZoneOffset.UTC, true)))
            .generator("second", segment("db", new CodecSpec.Prefix("O"), new CodecSpec.Radix(62, 11, true)))
            .generator("radix", segment("db", new CodecSpec.Radix(99, 11, true)))
            .generator("custom", segment("db", new CodecSpec.Custom("com.example.NoSuchCodec")))
            .generator("notCodec", segment("db", new CodecSpec.Custom(String.class.getName())))
            .generator("date", segment("db", new CodecSpec.DatePrefix("yyyyMMdd", "", DatePrefix.Source.ID_TIME, ZoneOffset.UTC)))
            .generator("bill", GeneratorSpec.PeriodicSegment.builder("db", Period.YEAR, ZoneOffset.UTC)
                .batch(BatchPolicy.DEFAULT)
                .periodic(PeriodicPolicy.DEFAULT)
                .codec(new GroupedCodecSpec.DatePrefix("", List.of(new CodecSpec.Friendly(ZoneOffset.UTC, false))))
                .build())
            .build());
        assertThat(message, containsString("generator [friendly]: A friendly codec needs a time-based generator"));
        assertThat(message, containsString("generator [second]: Radix[radix=62, width=11, padStart=true] turns the id into text, so it must be the first codec"));
        assertThat(message, containsString("generator [radix]: radix must be in [2, 62]: 99"));
        assertThat(message, containsString("generator [custom]: No codec [com.example.NoSuchCodec]"));
        assertThat(message, containsString("generator [notCodec]: [java.lang.String] is not an IdCodec"));
        assertThat(message, containsString("generator [date]: A date prefix from the id time needs a time-based generator"));
        assertThat(message, containsString("generator [bill]: A friendly codec needs a time-based generator"));
    }

    @Test
    void machineSpecsAreChecked() {
        RuntimeSpec.Builder generators = RuntimeSpec.builder("app").generator("default", snowflake(Layout.SNOWFLAKE_MS));
        assertThat(problems(generators.machine(MachineSpec.Static.builder(0).bits(0).ahead(Duration.ofSeconds(10)).build()).build()),
            containsString("machine: bits must be in [1, 30]: 0"));
        String fixed = problems(generators.machine(MachineSpec.Static.builder(8).bits(3).ahead(Duration.ofMillis(1)).build()).build());
        assertThat(fixed, containsString("machine: slot must be in [0, 8): 8"));
        assertThat(fixed, containsString("machine: ahead must be at least 2ms: PT0.001S"));
        String leased = problems(generators.store("m", store("memory")).machine(MachineSpec.Leased.builder("m")
            .bits(4)
            .ttl(Duration.ZERO)
            .tolerance(Duration.ofSeconds(-1))
            .preferredSlot(16)
            .ownerKey(" ")
            .build()).build());
        assertThat(leased, containsString("machine: ttl must be positive: PT0S"));
        assertThat(leased, containsString("machine: tolerance must not be negative: PT-1S"));
        assertThat(leased, containsString("machine: preferredSlot must be in [0, 16): 16"));
        assertThat(leased, containsString("machine: ownerKey must not be blank"));
    }

    @Test
    void namesAndClientsAreChecked() {
        RuntimeOptions base = options(memory);
        RuntimeOptions withClient = base.toBuilder().client("typo", new Object()).build();
        String message = problems(RuntimeSpec.builder("app")
            .store("db", store("memory"))
            .generator("a/b", segment("db"))
            .generator(" ", segment("db"))
            .build(), withClient);
        assertThat(message, containsString("generator [a/b]: the name must not be blank nor contain '/'"));
        assertThat(message, containsString("generator [ ]: the name must not be blank nor contain '/'"));
        assertThat(message, containsString("clients: [typo] is not a store of the spec"));
    }
}
