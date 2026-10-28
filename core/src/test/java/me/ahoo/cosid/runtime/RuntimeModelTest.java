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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.store.StoreConfig;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class RuntimeModelTest {
    @Test
    void specsCopyTheirCollections() {
        RuntimeSpec.Builder generators = RuntimeSpec.builder("app")
            .store("db", StoreSpec.builder("jdbc").timeout(Duration.ofSeconds(2)).build())
            .machine(MachineSpec.Leased.builder("db").bits(10).ttl(Duration.ofSeconds(30)).tolerance(Duration.ofSeconds(1)).build())
            .generator(RuntimeSpec.DEFAULT_GENERATOR, GeneratorSpec.Snowflake.builder()
                .layout(Layout.SNOWFLAKE_MS)
                .regression(ClockRegressionPolicy.DEFAULT)
                .codec(List.of(new CodecSpec.Radix(62, 11, true)))
                .build());
        RuntimeSpec spec = generators.build();
        generators.removeGenerator(RuntimeSpec.DEFAULT_GENERATOR);
        assertThat(spec.generators().size(), is(1));
        assertThat(spec.machine().get().bits(), is(10));
        assertThrows(IllegalArgumentException.class, () -> RuntimeSpec.builder(" ").build());
        assertThrows(UnsupportedOperationException.class, () -> spec.stores().put("x", null));
    }

    @Test
    void everySpecVariantBuilds() {
        List<CodecSpec> codecs = List.of(new CodecSpec.Radix(62, 11, true), new CodecSpec.Friendly(ZoneOffset.UTC, true),
            new CodecSpec.Prefix("O"), new CodecSpec.Suffix("X"), new CodecSpec.DatePrefix("yyyyMMdd", "-", DatePrefix.Source.NOW, ZoneOffset.UTC),
            new CodecSpec.Custom("com.example.Codec"), new CodecSpec.Instance(Radix.of(36, 13, true)));
        assertThat(GeneratorSpec.Segment.builder("db").batch(BatchPolicy.DEFAULT).codec(codecs).build().codec().size(), is(7));
        assertThat(GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Radix(62)).build().codec(), is(new PartsCodecSpec.Radix(62)));
        assertThat(new PartsCodecSpec.Friendly(ZoneOffset.UTC).zone(), is(ZoneOffset.UTC));
        GroupedCodecSpec grouped = new GroupedCodecSpec.DatePrefix("", List.of(new CodecSpec.Radix(10, 6, true)));
        assertThat(GeneratorSpec.PeriodicSegment.builder("db", Period.YEAR_MONTH_DAY, ZoneOffset.UTC)
            .batch(BatchPolicy.DEFAULT)
            .periodic(PeriodicPolicy.DEFAULT)
            .codec(grouped)
            .build().codec(), is(grouped));
        MachineSpec.Static machine = MachineSpec.Static.builder(3)
            .bits(10)
            .stateFile(Path.of("state"))
            .ahead(Duration.ofSeconds(10))
            .notBefore(Instant.EPOCH)
            .build();
        assertThat(machine.slot(), is(3));
        assertThrows(NullPointerException.class, () -> new CodecSpec.Prefix(null));
    }

    @Test
    void defaultOptions() {
        RuntimeOptions options = RuntimeOptions.defaults();
        assertThat(options.time(), sameInstance(TimeSource.SYSTEM));
        assertThat(options.listener(), sameInstance(GeneratorListener.NOOP));
        assertThat(options.executor(), is(Optional.empty()));
        assertThat(options.codecs().apply("anything"), is(Optional.empty()));
        assertThat(options.storeProviders().isEmpty(), is(true));
    }

    @Test
    void storeConfigAndInfoCopyTheirMaps() {
        Map<String, Object> options = new HashMap<>(Map.of("initSchema", true));
        StoreConfig config = StoreConfig.builder("db", "jdbc").timeout(Duration.ofSeconds(2)).options(options).build();
        options.clear();
        assertThat(config.options().get("initSchema"), is(true));
        assertThat(GeneratorInfo.builder("order", "segment").store("db").detail("step", 100).build().details().get("step"), is(100));
    }

    @Test
    void listenerDefaultsDoNothing() {
        GeneratorListener listener = GeneratorListener.NOOP;
        SlotLease lease = SlotLease.builder()
            .namespace("app")
            .slot(1)
            .owner(new me.ahoo.cosid.lease.Owner("host", java.util.UUID.randomUUID()))
            .fence(1)
            .expiresAt(0)
            .floor(0)
            .ceiling(0)
            .storeNow(0)
            .build();
        listener.onRangeReserved("order", "app/order", new Range(0, 100), Duration.ZERO);
        listener.onRefillFailed("order", "app/order", new IllegalStateException());
        listener.onBatchResized("order", 100, 200);
        listener.onSequenceExhausted("order");
        listener.onClockRegression("order", 1, "borrow");
        listener.onWindowExceeded("order");
        listener.onSlotAcquired(lease, "lowest");
        listener.onSlotRenewed(lease);
        listener.onSlotRenewFailed(lease, new IllegalStateException());
        listener.onSlotLost(lease);
        listener.onSlotReleased(lease);
        listener.onClockSkew("app", 1);
        listener.onDuplicateOwnerKey("app", "host");
        listener.onStoreTimeout("db", "reserve");
        assertThat(listener, sameInstance(GeneratorListener.NOOP));
    }
}
