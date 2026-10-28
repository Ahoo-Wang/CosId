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

package me.ahoo.cosid.spring.autoconfigure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.PartsCodecSpec;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link CosIdSpecs}: the pure mapping of {@link CosIdProperties}, from records built by hand and from bound properties.
 */
class CosIdSpecsTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** Binds {@code cosid.*} as Spring Boot does, without a context. */
    static CosIdProperties bind(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : pairs) {
            int at = pair.indexOf('=');
            map.put(pair.substring(0, at), pair.substring(at + 1));
        }
        return new Binder(new MapConfigurationPropertySource(map)).bindOrCreate(CosIdProperties.PREFIX, Bindable.of(CosIdProperties.class));
    }

    static RuntimeSpec spec(String... pairs) {
        return CosIdSpecs.toSpec(bind(pairs), ZONE);
    }

    static CosIdException invalid(String... pairs) {
        return assertThrows(CosIdException.class, () -> spec(pairs));
    }

    private static CosIdProperties.Generator generator(String strategy, String store, List<CosIdProperties.Codec> codec) {
        return new CosIdProperties.Generator(strategy, false, "SNOWFLAKE_MS", null, null, null, null, null, null, null, store, 100, 1,
            Duration.ofSeconds(1), null, Duration.ofSeconds(3), null, null, null, Duration.ofMinutes(1), null, codec);
    }

    @Test
    void mapsHandBuiltRecords() {
        CosIdProperties properties = new CosIdProperties(true, "app",
            Map.of("db", new CosIdProperties.Store("jdbc", Duration.ofSeconds(2), null, Map.of("initSchema", "true"))),
            new CosIdProperties.Machine(null, new CosIdProperties.Static(10, 3, null, Duration.ofSeconds(10), null)),
            Map.of("default", generator("snowflake", null, null),
                "order", generator("segment", "db", List.of(new CosIdProperties.Codec(null, null, "O", null, null, null, null)))),
            null);
        RuntimeSpec spec = CosIdSpecs.toSpec(properties, ZONE);
        assertEquals("app", spec.namespace());
        assertEquals(StoreSpec.builder("jdbc").timeout(Duration.ofSeconds(2)).option("initSchema", "true").build(), spec.stores().get("db"));
        assertEquals(Optional.of(MachineSpec.Static.builder(3).bits(10).ahead(Duration.ofSeconds(10)).build()), spec.machine());
        assertEquals(GeneratorSpec.Snowflake.builder()
            .layout(Layout.SNOWFLAKE_MS)
            .regression(ClockRegressionPolicy.DEFAULT)
            .codec(List.of())
            .build(), spec.generators().get("default"));
        assertEquals(GeneratorSpec.Segment.builder("db").batch(BatchPolicy.DEFAULT).codec(List.of(new CodecSpec.Prefix("O"))).build(), spec.generators().get("order"));
        assertEquals(new CosIdProperties.Metrics(false), properties.metrics());
    }

    @Test
    void mapsTheExampleOfTheDesign() {
        // 07-runtime-and-spring.md §4.1, with cosid80's codec as a one-item list.
        RuntimeSpec spec = spec(
            "cosid.namespace=app",
            "cosid.stores.db.type=jdbc",
            "cosid.stores.db.timeout=2s",
            "cosid.stores.cache.type=redis",
            "cosid.stores.cache.timeout=1s",
            "cosid.stores.cache.options.wait=auto",
            "cosid.machine.leased.store=cache",
            "cosid.machine.leased.bits=10",
            "cosid.machine.leased.ttl=30s",
            "cosid.machine.leased.tolerance=1s",
            "cosid.machine.leased.preferred-slot=",
            "cosid.generators.default.strategy=snowflake",
            "cosid.generators.default.layout=SNOWFLAKE_MS",
            "cosid.generators.default.codec[0].radix.radix=62",
            "cosid.generators.default.codec[0].radix.width=11",
            "cosid.generators.default.codec[0].radix.pad-start=true",
            "cosid.generators.order.strategy=segment",
            "cosid.generators.order.store=db",
            "cosid.generators.order.step=100",
            "cosid.generators.order.lookahead=1s",
            "cosid.generators.order.codec[0].radix.radix=62",
            "cosid.generators.order.codec[1].prefix=O",
            "cosid.generators.trace.strategy=snowflake",
            "cosid.generators.trace.layout=SNOWFLAKE_MS",
            "cosid.generators.trace.codec[0].friendly.zone=Asia/Shanghai",
            "cosid.generators.bill.strategy=periodic-segment",
            "cosid.generators.bill.store=db",
            "cosid.generators.bill.period=YEAR_MONTH_DAY",
            "cosid.generators.bill.zone=Asia/Shanghai",
            "cosid.generators.bill.codec[0].date-prefix.delimiter=",
            "cosid.generators.bill.codec[1].radix.radix=10",
            "cosid.generators.bill.codec[1].radix.width=6",
            "cosid.generators.bill.codec[1].radix.pad-start=true",
            "cosid.generators.token.strategy=cosid80",
            "cosid.generators.token.codec[0].radix.radix=62",
            "cosid.metrics.generated=false");
        assertEquals("app", spec.namespace());
        assertEquals(StoreSpec.builder("redis").timeout(Duration.ofSeconds(1)).option("wait", "auto").build(), spec.stores().get("cache"));
        assertEquals(Optional.of(MachineSpec.Leased.builder("cache").bits(10).ttl(Duration.ofSeconds(30)).tolerance(Duration.ofSeconds(1)).build()), spec.machine());
        assertEquals(GeneratorSpec.Snowflake.builder()
            .layout(Layout.SNOWFLAKE_MS)
            .regression(ClockRegressionPolicy.DEFAULT)
            .codec(List.of(new CodecSpec.Radix(62, 11, true)))
            .build(),
            spec.generators().get("default"));
        assertEquals(GeneratorSpec.Segment.builder("db")
            .batch(BatchPolicy.DEFAULT)
            .codec(List.of(new CodecSpec.Radix(62, Radix.maxWidth(62), false), new CodecSpec.Prefix("O")))
            .build(), spec.generators().get("order"));
        assertEquals(GeneratorSpec.Snowflake.builder()
            .layout(Layout.SNOWFLAKE_MS)
            .regression(ClockRegressionPolicy.DEFAULT)
            .codec(List.of(new CodecSpec.Friendly(ZONE, true)))
            .build(),
            spec.generators().get("trace"));
        assertEquals(GeneratorSpec.PeriodicSegment.builder("db", Period.YEAR_MONTH_DAY, ZONE)
            .batch(BatchPolicy.DEFAULT)
            .periodic(PeriodicPolicy.DEFAULT)
            .codec(new GroupedCodecSpec.DatePrefix("", List.of(new CodecSpec.Radix(10, 6, true))))
            .build(),
            spec.generators().get("bill"));
        assertEquals(GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Radix(62)).build(), spec.generators().get("token"));
    }

    @Test
    void mapsEverySetting() {
        RuntimeSpec spec = spec(
            "cosid.machine.static.bits=8",
            "cosid.machine.static.slot=7",
            "cosid.machine.static.state-file=/var/lib/cosid/state",
            "cosid.machine.static.ahead=5s",
            "cosid.machine.static.not-before=2026-11-01T00:00:00Z",
            "cosid.stores.mem.type= memory ",
            "cosid.generators.custom.strategy=SNOWFLAKE",
            "cosid.generators.custom.layout=snowflake_s",
            "cosid.generators.custom.epoch=2020-01-01T00:00:00Z",
            "cosid.generators.custom.unit=MILLIS",
            "cosid.generators.custom.time-bits=40",
            "cosid.generators.custom.node-bits=8",
            "cosid.generators.custom.seq-bits=15",
            "cosid.generators.custom.seq-reset-threshold=7",
            "cosid.generators.custom.regression.borrow-up-to=0ms",
            "cosid.generators.custom.regression.wait-up-to=2s",
            "cosid.generators.custom.codec[0].custom=myCodec",
            "cosid.generators.custom.codec[1].suffix=Z",
            "cosid.generators.custom.codec[2].date-prefix.pattern=yyMM",
            "cosid.generators.custom.codec[2].date-prefix.source=NOW",
            "cosid.generators.seg.strategy=segment",
            "cosid.generators.seg.store=mem",
            "cosid.generators.seg.step=10",
            "cosid.generators.seg.initial-offset=5",
            "cosid.generators.seg.lookahead=0s",
            "cosid.generators.seg.max-batch=10",
            "cosid.generators.seg.reserve-timeout=5s",
            "cosid.generators.seg.range-ttl=1h",
            "cosid.generators.year.strategy=periodic-segment",
            "cosid.generators.year.store=mem",
            "cosid.generators.year.period=year",
            "cosid.generators.year.prewarm=0s",
            "cosid.generators.year.seal-after=2d",
            "cosid.generators.friendly80.strategy=cosid80",
            "cosid.generators.friendly80.codec[0].friendly.pad-start=false");
        assertEquals(Optional.of(MachineSpec.Static.builder(7)
            .bits(8)
            .stateFile(Path.of("/var/lib/cosid/state"))
            .ahead(Duration.ofSeconds(5))
            .notBefore(Instant.parse("2026-11-01T00:00:00Z"))
            .build()), spec.machine());
        assertEquals("memory", spec.stores().get("mem").type());
        assertEquals(GeneratorSpec.Snowflake.builder()
            .layout(new Layout(Instant.parse("2020-01-01T00:00:00Z"), ChronoUnit.MILLIS, 40, 8, 15, 7))
            .regression(new ClockRegressionPolicy(Duration.ZERO, Duration.ofSeconds(2)))
            .codec(List.of(new CodecSpec.Custom("myCodec"), new CodecSpec.Suffix("Z"), new CodecSpec.DatePrefix("yyMM", "-", DatePrefix.Source.NOW, ZONE)))
            .build(),
            spec.generators().get("custom"));
        assertEquals(GeneratorSpec.Segment.builder("mem")
            .batch(BatchPolicy.builder() .step(10) .initialOffset(5) .lookahead(Duration.ZERO) .maxBatch(10) .reserveTimeout(Duration.ofSeconds(5)) .rangeTtl(Duration.ofHours(1)) .build())
            .codec(List.of())
            .build(), spec.generators().get("seg"));
        assertEquals(GeneratorSpec.PeriodicSegment.builder("mem", Period.YEAR, ZONE)
            .batch(BatchPolicy.DEFAULT)
            .periodic(PeriodicPolicy.builder().prewarm(Duration.ZERO).sealAfter(Duration.ofDays(2)).build())
            .codec(new GroupedCodecSpec.DatePrefix("-", List.of()))
            .build(), spec.generators().get("year"));
        assertEquals(GeneratorSpec.CosId80.builder().regression(ClockRegressionPolicy.DEFAULT).codec(new PartsCodecSpec.Friendly(ZONE)).build(), spec.generators().get("friendly80"));
    }

    @Test
    void keepsTheSeqResetThresholdOfAPresetWhoseSeqBitsStay() {
        GeneratorSpec spec = spec("cosid.generators.a.strategy=snowflake", "cosid.generators.a.node-bits=9", "cosid.generators.a.time-bits=42")
            .generators().get("a");
        Layout layout = ((GeneratorSpec.Snowflake) spec).layout();
        assertEquals(Layout.SNOWFLAKE_MS.seqResetThreshold(), layout.seqResetThreshold());
        assertEquals(9, layout.nodeBits());
    }

    @Test
    void passesStoreOptionsThroughAsTheyAreWritten() {
        // The Mongo write concern (06-stores.md §2.3) is a backend option: it reaches the StoreProvider unchanged.
        RuntimeSpec spec = spec("cosid.stores.mongo.type=mongo", "cosid.stores.mongo.options.uri=mongodb://db/?replicaSet=rs",
            "cosid.stores.mongo.options.database=ids", "cosid.stores.mongo.options.writeConcern=w1");
        assertEquals(StoreSpec.builder("mongo")
            .timeout(Duration.ofSeconds(2))
            .option("uri", "mongodb://db/?replicaSet=rs")
            .option("database", "ids")
            .option("writeConcern", "w1")
            .build(), spec.stores().get("mongo"));
    }

    @Test
    void overridesTheSeqResetThresholdAlone() {
        Layout layout = ((GeneratorSpec.Snowflake) spec("cosid.generators.a.strategy=snowflake", "cosid.generators.a.seq-reset-threshold=0")
            .generators().get("a")).layout();
        assertEquals(new Layout(Layout.SNOWFLAKE_MS.epoch(), Layout.SNOWFLAKE_MS.unit(), Layout.SNOWFLAKE_MS.timeBits(), Layout.SNOWFLAKE_MS.nodeBits(),
            Layout.SNOWFLAKE_MS.seqBits(), 0), layout);
        assertThat(invalid("cosid.generators.b.strategy=snowflake", "cosid.generators.b.seq-bits=4", "cosid.generators.b.seq-reset-threshold=16")
            .getMessage(), containsString("cosid.generators.b: seqResetThreshold must be in [0, 15]: 16"));
    }

    @Test
    void mapsALeasedMachineWithAllItsSettings() {
        RuntimeSpec spec = spec("cosid.machine.leased.store=s", "cosid.machine.leased.bits=6", "cosid.machine.leased.ttl=10s",
            "cosid.machine.leased.tolerance=0s", "cosid.machine.leased.preferred-slot=4", "cosid.machine.leased.owner-key=pod-4");
        assertEquals(Optional.of(MachineSpec.Leased.builder("s")
            .bits(6)
            .ttl(Duration.ofSeconds(10))
            .tolerance(Duration.ZERO)
            .preferredSlot(4)
            .ownerKey("pod-4")
            .build()),
            spec.machine());
    }

    @Test
    void defaultsToNothing() {
        RuntimeSpec spec = spec();
        assertEquals(CosIdProperties.DEFAULT_NAMESPACE, spec.namespace());
        assertTrue(spec.stores().isEmpty());
        assertTrue(spec.machine().isEmpty());
        assertTrue(spec.generators().isEmpty());
        assertTrue(bind().enabled());
    }

    @Test
    void wrapsADisabledGeneratorAndSkipsItsChecks() {
        RuntimeSpec spec = spec(
            "cosid.generators.off.strategy=segment",
            "cosid.generators.off.disabled=true",
            "cosid.generators.broken.strategy=periodic-segment",
            "cosid.generators.broken.disabled=true",
            "cosid.generators.broken.codec[0].prefix=x",
            "cosid.generators.ok.strategy=snowflake",
            "cosid.generators.ok.disabled=true");
        assertThat(spec.generators().get("off"), instanceOf(GeneratorSpec.Disabled.class));
        assertThat(((GeneratorSpec.Disabled) spec.generators().get("off")).generator(), instanceOf(GeneratorSpec.Segment.class));
        assertThat(((GeneratorSpec.Disabled) spec.generators().get("broken")).generator(), instanceOf(GeneratorSpec.PeriodicSegment.class));
        assertEquals(new GeneratorSpec.Disabled(GeneratorSpec.Snowflake.builder()
            .layout(Layout.SNOWFLAKE_MS)
            .regression(ClockRegressionPolicy.DEFAULT)
            .codec(List.of())
            .build()),
            spec.generators().get("ok"));
        RuntimeSpec placeholders = spec(
            "cosid.generators.a.strategy=snowflake", "cosid.generators.a.disabled=true", "cosid.generators.a.layout=nope",
            "cosid.generators.b.strategy=cosid80", "cosid.generators.b.disabled=true", "cosid.generators.b.codec[0].prefix=x");
        assertThat(((GeneratorSpec.Disabled) placeholders.generators().get("a")).generator(), instanceOf(GeneratorSpec.Snowflake.class));
        assertThat(((GeneratorSpec.Disabled) placeholders.generators().get("b")).generator(), instanceOf(GeneratorSpec.CosId80.class));
    }

    @Test
    void reportsEveryProblemByPropertyPath() {
        CosIdException failure = invalid(
            "cosid.namespace= ",
            "cosid.stores.s.timeout=1s",
            "cosid.machine.leased.store=s",
            "cosid.machine.static.slot=1",
            "cosid.generators.a.strategy=uuid",
            "cosid.generators.b.strategy=segment",
            "cosid.generators.c.strategy=snowflake",
            "cosid.generators.c.layout=SNOWFLAKE_US",
            "cosid.generators.c.codec[0].prefix=x",
            "cosid.generators.c.codec[0].suffix=y",
            "cosid.generators.c.codec[1].radix.radix=1",
            "cosid.generators.d.strategy=periodic-segment",
            "cosid.generators.d.store=s",
            "cosid.generators.d.period=WEEK",
            "cosid.generators.d.codec[0].radix.radix=10",
            "cosid.generators.e.strategy=cosid80",
            "cosid.generators.e.codec[0].prefix=x",
            "cosid.generators.f.strategy=segment",
            "cosid.generators.f.store=s",
            "cosid.generators.f.step=0",
            "cosid.generators.g.disabled=true");
        String message = failure.getMessage();
        assertThat(message, containsString("Invalid cosid.* configuration"));
        assertThat(message, containsString("cosid.namespace: must not be blank"));
        assertThat(message, containsString("cosid.stores.s.type: required"));
        assertThat(message, containsString("cosid.machine: set either leased or static, not both"));
        assertThat(message, containsString("cosid.generators.a.strategy: one of snowflake, cosid80, segment, periodic-segment, not [uuid]"));
        assertThat(message, containsString("cosid.generators.b.store: required"));
        assertThat(message, containsString("cosid.generators.c.layout: one of [JS_SAFE_MS, JS_SAFE_S, SNOWFLAKE_MS, SNOWFLAKE_S], not [SNOWFLAKE_US]"));
        assertThat(message, containsString("cosid.generators.c.codec[0]: set exactly one of radix, friendly, prefix, suffix, date-prefix, custom, period (2 set)"));
        assertThat(message, containsString("cosid.generators.c.codec[1].radix.radix: must be in [2, 62]: 1"));
        assertThat(message, containsString("cosid.generators.d.period: one of [YEAR, YEAR_MONTH, YEAR_MONTH_DAY], not [WEEK]"));
        assertThat(message, containsString("cosid.generators.d.codec[0]: the codec list of a periodic segment has a period item or starts with date-prefix"));
        assertThat(message, containsString("cosid.generators.e.codec: a cosid80 generator takes a single radix or friendly item"));
        assertThat(message, containsString("cosid.generators.f: step must be positive: 0"));
        assertThat(message, containsString("cosid.generators.g.strategy: one of"));
    }

    @Test
    void mapsThePeriodAfterABusinessPrefix() {
        // 4.x group-prefix.before-prefix = true: the value codecs, the period, then the decorations of the whole.
        RuntimeSpec spec = spec("cosid.generators.p.strategy=periodic-segment", "cosid.generators.p.store=s", "cosid.generators.p.period=YEAR",
            "cosid.generators.p.codec[0].radix.radix=10", "cosid.generators.p.codec[0].radix.width=6", "cosid.generators.p.codec[0].radix.pad-start=true",
            "cosid.generators.p.codec[1].period.delimiter=/",
            "cosid.generators.p.codec[2].prefix=BIZ-",
            "cosid.generators.p.codec[3].suffix=!",
            "cosid.generators.q.strategy=periodic-segment", "cosid.generators.q.store=s", "cosid.generators.q.period=YEAR",
            "cosid.generators.q.codec[0].period.delimiter=");
        assertEquals(new GroupedCodecSpec.DatePrefix("/", List.of(new CodecSpec.Radix(10, 6, true)),
            List.of(new CodecSpec.Prefix("BIZ-"), new CodecSpec.Suffix("!"))), ((GeneratorSpec.PeriodicSegment) spec.generators().get("p")).codec());
        assertEquals(new GroupedCodecSpec.DatePrefix("", List.of(), List.of()), ((GeneratorSpec.PeriodicSegment) spec.generators().get("q")).codec());
    }

    @Test
    void mapsAPeriodLeftOutOfTheText() {
        // 4.x group-prefix.enabled = false: the value codecs alone, wherever the period item stands.
        RuntimeSpec spec = spec("cosid.generators.p.strategy=periodic-segment", "cosid.generators.p.store=s", "cosid.generators.p.period=YEAR",
            "cosid.generators.p.codec[0].radix.radix=36",
            "cosid.generators.p.codec[1].period.enabled=false",
            "cosid.generators.p.codec[2].prefix=O");
        assertEquals(new GroupedCodecSpec.ValueOnly(List.of(new CodecSpec.Radix(36, Radix.maxWidth(36), false), new CodecSpec.Prefix("O"))),
            ((GeneratorSpec.PeriodicSegment) spec.generators().get("p")).codec());
    }

    @Test
    void reportsMisplacedPeriodItems() {
        String message = invalid("cosid.generators.a.strategy=segment", "cosid.generators.a.store=s", "cosid.generators.a.codec[0].period.delimiter=-",
            "cosid.generators.b.strategy=periodic-segment", "cosid.generators.b.store=s", "cosid.generators.b.period=YEAR",
            "cosid.generators.b.codec[0].period.delimiter=-", "cosid.generators.b.codec[1].period.delimiter=/",
            "cosid.generators.c.strategy=periodic-segment", "cosid.generators.c.store=s", "cosid.generators.c.period=YEAR",
            "cosid.generators.c.codec[0].period.delimiter=-", "cosid.generators.c.codec[0].prefix=x").getMessage();
        assertThat(message, containsString("cosid.generators.a.codec[0].period: only a periodic segment has a period item, and only one"));
        assertThat(message, containsString("cosid.generators.b.codec[1].period: only a periodic segment has a period item, and only one"));
        assertThat(message, containsString("cosid.generators.c.codec[0]: set exactly one"));
    }

    @Test
    void reportsMissingMachineFields() {
        assertThat(invalid("cosid.machine.leased.bits=3").getMessage(), containsString("cosid.machine.leased.store: required"));
        assertThat(invalid("cosid.machine.static.bits=3").getMessage(), containsString("cosid.machine.static.slot: required"));
    }

    @Test
    void reportsPeriodicCodecProblemsAtTheirIndex() {
        String message = invalid("cosid.generators.p.strategy=periodic-segment", "cosid.generators.p.store=s", "cosid.generators.p.period=YEAR",
            "cosid.generators.p.codec[0].date-prefix.delimiter=-", "cosid.generators.p.codec[1].prefix=a", "cosid.generators.p.codec[1].suffix=b",
            "cosid.generators.q.strategy=periodic-segment", "cosid.generators.q.store=s", "cosid.generators.q.period=YEAR",
            "cosid.generators.q.codec[0].prefix=a", "cosid.generators.q.codec[0].suffix=b",
            "cosid.generators.r.strategy=cosid80", "cosid.generators.r.codec[0].radix.radix=62", "cosid.generators.r.codec[1].radix.radix=36")
            .getMessage();
        assertThat(message, containsString("cosid.generators.p.codec[1]: set exactly one"));
        assertThat(message, containsString("cosid.generators.q.codec[0]: set exactly one"));
        assertThat(message, containsString("cosid.generators.r.codec: a cosid80 generator takes a single radix or friendly item"));
    }
}
