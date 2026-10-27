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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IntIdGenerator;
import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

class CosIdRuntimeTest {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final Duration TIMEOUT = Duration.ofSeconds(1);

    static RuntimeOptions options(StoreProvider... providers) {
        return options(TimeSource.SYSTEM, GeneratorListener.NOOP, providers);
    }

    static RuntimeOptions options(TimeSource time, GeneratorListener listener, StoreProvider... providers) {
        RuntimeOptions defaults = RuntimeOptions.defaults();
        return new RuntimeOptions(time, Optional.empty(), listener, List.of(providers), Map.of(), () -> "host-a",
            OptionalInt.empty(), defaults.codecs());
    }

    static MachineSpec.Leased leased(String store) {
        return new MachineSpec.Leased(store, 10, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty(), Optional.empty());
    }

    static GeneratorSpec.Segment segment(String store, CodecSpec... codec) {
        return new GeneratorSpec.Segment(store, BatchPolicy.DEFAULT, List.of(codec));
    }

    static GeneratorSpec.Snowflake snowflake(Layout layout, CodecSpec... codec) {
        return new GeneratorSpec.Snowflake(layout, ClockRegressionPolicy.DEFAULT, List.of(codec));
    }

    static StoreSpec store(String type) {
        return new StoreSpec(type, TIMEOUT, Map.of());
    }

    @Test
    void mixedStrategiesOnMixedStores() {
        TestStores db = new TestStores("db-type");
        TestStores cache = new TestStores("cache-type");
        RuntimeSpec spec = new RuntimeSpec("app",
            Map.of("db", store("db-type"), "cache", store("cache-type"), "unused", store("nowhere")),
            Optional.of(leased("cache")),
            Map.of(
                "default", snowflake(Layout.SNOWFLAKE_MS, new CodecSpec.Radix(62, 11, true)),
                "seconds", snowflake(Layout.SNOWFLAKE_S),
                "trace", snowflake(Layout.SNOWFLAKE_MS, new CodecSpec.Friendly(SHANGHAI, true)),
                "token", new GeneratorSpec.CosId80(ClockRegressionPolicy.DEFAULT, new PartsCodecSpec.Radix(62)),
                "order", segment("db", new CodecSpec.Radix(62, 11, false), new CodecSpec.Prefix("O")),
                "bill", new GeneratorSpec.PeriodicSegment("cache", Period.YEAR_MONTH_DAY, SHANGHAI, BatchPolicy.DEFAULT,
                    PeriodicPolicy.DEFAULT, new GroupedCodecSpec.DatePrefix("", List.of(new CodecSpec.Radix(10, 6, true))))));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(db, cache))) {
            assertThat(runtime.namespace(), is("app"));
            // Only the stores in use are opened; the unknown type of "unused" is never looked up.
            assertThat(db.events, contains("open:db"));
            assertThat(cache.events, contains("open:cache"));

            SlotLease lease = runtime.slot().orElseThrow();
            assertThat(lease.namespace(), is("app"));
            assertThat(lease.owner().key(), startsWith("host-a:"));

            // Snowflake: long ids, the codec, the slot of the lease.
            IdGenerator ids = runtime.generator("default");
            long first = ids.generate();
            assertThat(ids.generate(), greaterThan(first));
            TimeBasedGenerator snowflake = runtime.get("default", TimeBasedGenerator.class);
            assertThat(snowflake, sameInstance(ids));
            assertThat(snowflake.slot(), is(lease.slot()));
            assertThat(Layout.SNOWFLAKE_MS.unpack(first).node(), is(lease.slot()));
            StringIdGenerator strings = runtime.stringGenerator("default");
            String text = strings.generate();
            assertThat(text.length(), is(11));
            assertThat(strings.codec().decode(text), greaterThan(first));
            assertThat(runtime.get("default", IdCodec.class), sameInstance(strings.codec()));
            assertThat(runtime.get("default", IntIdGenerator.class), instanceOf(IntIdGenerator.class));
            assertThat(runtime.get("seconds", TimeBasedGenerator.class).layout(), is(Layout.SNOWFLAKE_S));
            assertThat(runtime.stringGenerator("seconds").generate(), is(not("")));
            String friendly = runtime.stringGenerator("trace").generate();
            assertThat(runtime.stringGenerator("trace").codec().decode(friendly), greaterThan(0L));

            // CosId80: text and parts only.
            TimeBasedGenerator token = runtime.get("token", TimeBasedGenerator.class);
            IdParts parts = token.nextParts();
            assertThat(parts.node(), is(lease.slot()));
            PartsCodec partsCodec = runtime.get("token", PartsCodec.class);
            assertThat(partsCodec.decode(partsCodec.encode(parts)), is(parts));
            assertThrows(NoSuchElementException.class, () -> runtime.generator("token"));
            assertThrows(NoSuchElementException.class, () -> runtime.stringGenerator("token"));

            // Segment: keys under the namespace, codec list in order.
            long order = runtime.generator("order").generate();
            assertThat(runtime.generator("order").generate(), is(order + 1));
            assertThat(runtime.get("order", SegmentGenerator.class).key(), is("app/order"));
            assertThat(db.backing("db").reserve("app/order", 1, 0).start(), greaterThan(order));
            String orderText = runtime.stringGenerator("order").generate();
            assertThat(orderText, startsWith("O"));
            assertThat(runtime.stringGenerator("order").codec().decode(orderText), is(order + 2));

            // Periodic segment: the empty delimiter decodes thanks to the period's key length.
            PeriodicSegmentGenerator bill = runtime.periodic("bill");
            GroupedId billId = bill.generate();
            assertThat(billId.period(), is(Period.YEAR_MONTH_DAY.keyOf(Instant.now(), SHANGHAI)));
            GroupedCodec billCodec = runtime.get("bill", GroupedCodec.class);
            String billText = billCodec.encode(billId);
            assertThat(billText.length(), is(14));
            assertThat(billCodec.decode(billText), is(billId));
            assertThrows(NoSuchElementException.class, () -> runtime.generator("bill"));
            assertThrows(NoSuchElementException.class, () -> runtime.periodic("order"));

            assertThrows(NoSuchElementException.class, () -> runtime.generator("missing"));
            assertThrows(NoSuchElementException.class, () -> runtime.get("order", PeriodicSegmentGenerator.class));

            Map<String, GeneratorInfo> infos = runtime.describe();
            assertThat(List.copyOf(infos.keySet()), contains("bill", "default", "order", "seconds", "token", "trace"));
            assertThat(infos.get("default").strategy(), is("snowflake"));
            assertThat(infos.get("default").store(), is(""));
            assertThat(infos.get("default").details().get("slot"), is(lease.slot()));
            assertThat(infos.get("default").details().get("nodeBits"), is(10));
            assertThat(infos.get("token").strategy(), is("cosid80"));
            assertThat(infos.get("order").strategy(), is("segment"));
            assertThat(infos.get("order").store(), is("db"));
            assertThat(infos.get("order").details().get("key"), is("app/order"));
            assertThat((Long) infos.get("order").details().get("held"), greaterThan(0L));
            assertThat(infos.get("bill").strategy(), is("periodic-segment"));
            assertThat(infos.get("bill").details().get("period"), is(billId.period()));
            assertThat(infos.get("bill").details().get("key"), is("app/bill"));
        }
        assertThat(db.events, contains("open:db", "close:db"));
        assertThat(cache.events, contains("open:cache", "close:cache"));
    }

    @Test
    void noTimeBasedGeneratorMeansNoSlot() {
        TestStores db = new TestStores("memory");
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("db", store("memory")), Optional.of(leased("db")),
            Map.of("order", segment("db")));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(db))) {
            assertThat(runtime.slot(), is(Optional.empty()));
            assertThat(db.backing("db").acquire("app", new me.ahoo.cosid.lease.Owner("other", java.util.UUID.randomUUID()), 1024,
                Duration.ofSeconds(1), Duration.ZERO, OptionalInt.empty()), instanceOf(me.ahoo.cosid.lease.AcquireResult.Acquired.class));
            assertThat(runtime.generator("order").generate(), is(0L));
        }
    }

    @Test
    void twoRuntimesInOneJvmNeverShareASlot() {
        List<String> duplicates = new CopyOnWriteArrayList<>();
        GeneratorListener listener = new GeneratorListener() {
            @Override
            public void onDuplicateOwnerKey(String namespace, String ownerKey) {
                duplicates.add(ownerKey);
            }
        };
        TestStores shared = new TestStores("memory");
        MachineSpec.Leased machine = new MachineSpec.Leased("cache", 10, Duration.ofSeconds(30), Duration.ofSeconds(1),
            OptionalInt.empty(), Optional.of("pod-0"));
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("cache", store("memory")), Optional.of(machine),
            Map.of("default", snowflake(Layout.SNOWFLAKE_MS)));
        try (CosIdRuntime one = CosIdRuntime.start(spec, options(TimeSource.SYSTEM, listener, shared));
             CosIdRuntime two = CosIdRuntime.start(spec, options(TimeSource.SYSTEM, listener, shared))) {
            SlotLease a = one.slot().orElseThrow();
            SlotLease b = two.slot().orElseThrow();
            assertThat(a.slot(), is(not(b.slot())));
            assertThat(a.owner().key(), is("pod-0"));
            assertThat(a.owner().incarnation(), is(not(b.owner().incarnation())));
            assertThat(duplicates, contains("pod-0"));
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(one.generator("default").generate());
                ids.add(two.generator("default").generate());
            }
            assertThat(ids.size(), is(2000));
        }
    }

    @Test
    void ownerKeyFromHostAndPort() {
        TestStores cache = new TestStores("memory");
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("cache", store("memory")), Optional.of(leased("cache")),
            Map.of("default", snowflake(Layout.SNOWFLAKE_MS)));
        RuntimeOptions base = options(cache);
        RuntimeOptions withPort = new RuntimeOptions(base.time(), base.executor(), base.listener(), base.storeProviders(),
            base.clients(), () -> "web-1", OptionalInt.of(8080), base.codecs());
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, withPort)) {
            assertThat(runtime.slot().orElseThrow().owner().key(), is("web-1:8080"));
        }
    }

    @Test
    void clientsReachTheirStores() {
        TestStores cache = new TestStores("memory");
        Object client = new Object();
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("cache", new StoreSpec("memory", TIMEOUT, Map.of("wait", "auto"))),
            Optional.empty(), Map.of("order", segment("cache")));
        RuntimeOptions base = options(cache);
        RuntimeOptions withClient = new RuntimeOptions(base.time(), base.executor(), base.listener(), base.storeProviders(),
            Map.of("cache", client), base.hostResolver(), base.port(), base.codecs());
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, withClient)) {
            assertThat(runtime.generator("order").generate(), is(0L));
            assertThat(cache.configs.getFirst().client().orElseThrow(), sameInstance(client));
            assertThat(cache.configs.getFirst().options().get("wait"), is("auto"));
            assertThat(cache.configs.getFirst().timeout(), is(TIMEOUT));
            assertThat(cache.configs.getFirst().type(), is("memory"));
        }
    }

    @Test
    void disabledGeneratorsAreNotCreated() {
        TestStores db = new TestStores("memory");
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("db", store("memory")), Optional.empty(),
            Map.of(
                "order", segment("db"),
                // Neither a machine nor the store it names is needed.
                "default", new GeneratorSpec.Disabled(snowflake(Layout.SNOWFLAKE_MS)),
                "bill", new GeneratorSpec.Disabled(segment("gone"))));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(db))) {
            assertThat(runtime.slot(), is(Optional.empty()));
            GeneratorDisabledException disabled = assertThrows(GeneratorDisabledException.class, () -> runtime.generator("default"));
            assertThat(disabled.generator(), is("default"));
            assertThrows(GeneratorDisabledException.class, () -> runtime.stringGenerator("default"));
            assertThrows(GeneratorDisabledException.class, () -> runtime.get("default", TimeBasedGenerator.class));
            assertThrows(GeneratorDisabledException.class, () -> runtime.periodic("bill"));
            GeneratorInfo info = runtime.describe().get("bill");
            assertThat(info.strategy(), is("segment"));
            assertThat(info.store(), is("gone"));
            assertThat(info.details(), is(Map.of("disabled", true)));
            assertThat(runtime.describe().get("default").strategy(), is("snowflake"));
            assertThat(runtime.generator("order").generate(), is(0L));
        }
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSpec.Disabled(new GeneratorSpec.Disabled(segment("db"))));
    }

    @Test
    void staticSlotWritesTheExactLastIssuedTime(@TempDir Path dir) throws Exception {
        Path state = dir.resolve("state");
        me.ahoo.cosid.store.tck.ManualClock clock = new me.ahoo.cosid.store.tck.ManualClock(1_800_000_000_000L);
        RuntimeSpec spec = new RuntimeSpec("app", Map.of(),
            Optional.of(new MachineSpec.Static(10, 7, Optional.of(state), Duration.ofSeconds(10), Optional.empty())),
            Map.of("default", snowflake(Layout.SNOWFLAKE_MS)));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(clock, GeneratorListener.NOOP))) {
            assertThat(runtime.slot(), is(Optional.empty()));
            clock.advance(Duration.ofMillis(5));
            long id = runtime.generator("default").generate();
            assertThat(Layout.SNOWFLAKE_MS.unpack(id).node(), is(7));
            assertThat(runtime.describe().get("default").details().get("slot"), is(7));
        }
        assertThat(Files.readString(state), containsString("1800000000005"));
    }

    @Test
    void listenerFailuresDoNotFailTheRuntime() {
        TestStores cache = new TestStores("memory");
        GeneratorListener failing = new GeneratorListener() {
            @Override
            public void onSlotAcquired(SlotLease lease, String reason) {
                throw new IllegalStateException("listener bug");
            }
        };
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("cache", store("memory")), Optional.of(leased("cache")),
            Map.of("default", snowflake(Layout.SNOWFLAKE_MS)));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(TimeSource.SYSTEM, failing, cache))) {
            assertThat(runtime.generator("default").generate(), greaterThan(0L));
        }
    }

    @Test
    void customCodecsByReference() {
        TestStores db = new TestStores("memory");
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("db", store("memory")), Optional.empty(),
            Map.of("byClass", segment("db", new CodecSpec.Custom(HexCodec.class.getName()), new CodecSpec.Suffix("h")),
                "byInstance", segment("db", new CodecSpec.Instance(new HexCodec()))));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(db))) {
            for (int i = 0; i < 10; i++) {
                runtime.generator("byClass").generate();
            }
            assertThat(runtime.stringGenerator("byClass").generate(), is("ah"));
            assertThat(runtime.stringGenerator("byInstance").generate(), is("0"));
        }
    }

    @Test
    void storeProvidersFromServiceLoaderAndExplicitOnesWin() {
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("db", store(ServiceLoadedStores.TYPE)), Optional.empty(),
            Map.of("order", segment("db")));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options())) {
            assertThat(runtime.generator("order").generate(), is(0L));
            assertThat(ServiceLoadedStores.OPENED.get(), greaterThan(0));
        }
        int loaded = ServiceLoadedStores.OPENED.get();
        TestStores explicit = new TestStores(ServiceLoadedStores.TYPE);
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options(explicit))) {
            assertThat(runtime.generator("order").generate(), is(0L));
        }
        assertThat(explicit.events, contains("open:db", "close:db"));
        assertThat(ServiceLoadedStores.OPENED.get(), is(loaded));
    }

    @Test
    void defaultOptionsStartARuntime() {
        RuntimeSpec spec = new RuntimeSpec("app", Map.of("db", store(ServiceLoadedStores.TYPE)), Optional.empty(),
            Map.of("order", segment("db")));
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, RuntimeOptions.defaults())) {
            assertThat(runtime.generator("order").generate(), is(0L));
        }
        assertThat(HostResolver.LOCAL.host(), is(not("")));
    }

    /**
     * A custom codec: lower-case hexadecimal.
     */
    public static final class HexCodec implements IdCodec {
        @Override
        public String encode(long id) {
            return Long.toHexString(id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text, 16);
        }
    }
}
