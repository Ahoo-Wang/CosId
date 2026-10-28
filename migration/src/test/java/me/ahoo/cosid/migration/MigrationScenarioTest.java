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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.migration.source.SourceReader;
import me.ahoo.cosid.migration.source.SourceSnapshot;
import me.ahoo.cosid.migration.source.Sources;
import me.ahoo.cosid.migration.target.NamespaceState;
import me.ahoo.cosid.migration.target.RangeState;
import me.ahoo.cosid.migration.target.TargetAdmin;
import me.ahoo.cosid.migration.target.Targets;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.MachineSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 09 §5 on one pair of stores: real 4.x data written by the 4.1.0 jars, migrated, then ids issued by a 5.0
 * {@link CosIdRuntime}; a 4.x instance left running; {@code apply} run again and after an interruption.
 */
abstract class MigrationScenarioTest {
    @TempDir
    Path dir;
    String namespace;
    TestBackend.Store source;
    TestBackend.Store target;
    Legacy4xWriter writer;
    final ByteArrayOutputStream report = new ByteArrayOutputStream();

    abstract TestBackend sourceBackend();

    TestBackend targetBackend() {
        return sourceBackend();
    }

    @BeforeEach
    void setUp() {
        namespace = "app" + UUID.randomUUID().toString().substring(0, 8);
        source = sourceBackend().store();
        target = targetBackend() == sourceBackend() ? source : targetBackend().store();
        writer = source.writer();
    }

    @AfterEach
    void tearDown() {
        if (writer != null) {
            writer.close();
        }
    }

    Migration migration() {
        return migration(Sources::open, Targets::open);
    }

    Migration migration(Function<StoreLocation, SourceReader> sources, Function<StoreLocation, TargetAdmin> targets) {
        return new Migration(sources, targets, Migration.Sleeper.THREAD, new PrintStream(report, true, StandardCharsets.UTF_8));
    }

    static Path resource(String name) {
        try {
            return Path.of(MigrationScenarioTest.class.getResource("/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code scenario-4x.yaml} with the distributors of the source store. */
    Path layout() {
        Path file = dir.resolve("scenario-4x.yaml");
        if (!Files.exists(file)) {
            try {
                Files.writeString(file, Files.readString(resource("scenario-4x.yaml")).replace("type: jdbc", "type: " + source.backend().legacyType()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return file;
    }

    Plan plan(Migration migration) {
        return migration.plan(new Migration.PlanOptions(source.location(), target.location(), namespace, layout(), null, null,
            Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), dir.resolve("plan.json")));
    }

    Migration.ApplyOptions apply(Duration observe, boolean seal) {
        return new Migration.ApplyOptions(dir.resolve("plan.json"), true, observe, 0, Duration.ZERO, seal, Migration.DEFAULT_SEAL_GAP,
            dir.resolve("apply-result.json"), Map.of(), Map.of(), layout(), null);
    }

    List<String> verify() {
        return migration().verify(new Migration.VerifyOptions(dir.resolve("plan.json"), dir.resolve("apply-result.json"), Map.of(), Map.of()));
    }

    CosIdRuntime runtime() {
        Map<String, GeneratorSpec> generators = new LinkedHashMap<>();
        generators.put("order", GeneratorSpec.Segment.builder("main").batch(BatchPolicy.DEFAULT).codec(List.of()).build());
        generators.put("big", GeneratorSpec.Segment.builder("main").batch(BatchPolicy.DEFAULT).codec(List.of()).build());
        generators.put("default", GeneratorSpec.Segment.builder("main").batch(BatchPolicy.DEFAULT).codec(List.of()).build());
        generators.put("bill", GeneratorSpec.PeriodicSegment.builder("main", Period.YEAR_MONTH_DAY, ZoneId.systemDefault())
            .batch(BatchPolicy.DEFAULT)
            .periodic(PeriodicPolicy.DEFAULT)
            .codec(new GroupedCodecSpec.DatePrefix("-", List.of()))
            .build());
        generators.put("snow", GeneratorSpec.Snowflake.builder()
            .layout(Layout.SNOWFLAKE_MS)
            .regression(ClockRegressionPolicy.DEFAULT)
            .codec(List.of())
            .build());
        RuntimeSpec.Builder spec = RuntimeSpec.builder(namespace)
            .store("main", target.spec())
            .machine(MachineSpec.Leased.builder("main").bits(10).ttl(Duration.ofSeconds(30)).tolerance(Duration.ofSeconds(1)).build());
        generators.forEach(spec::generator);
        return CosIdRuntime.start(spec.build(), RuntimeOptions.defaults());
    }

    @Test
    void migratesRealDataAndIssuesUniqueIds() {
        long order = writer.segment(namespace, "order", 0, 10, 5);
        long big = writer.segment(namespace, "big", 1_000_000, 100, 3);
        long share = writer.segment(namespace, "__share__", 0, 10, 2);
        Map<String, Object> grouped = writer.grouped(namespace, "bill", 5, 3);
        String today = (String) grouped.get("groupKey");
        long billToday = (Long) grouped.get("maxId");
        long billPast = writer.segment(namespace, "bill@20240101", 0, 5, 7);
        long billMonth = writer.segment(namespace, "bill@20250315", 0, 5, 1);
        Map<String, Object> machines = writer.machines(namespace, 3, 3000);
        long snowflakeMax = (Long) machines.get("maxId");
        assertThat(order, is(50L));
        assertThat(big, is(1_000_300L));

        Plan plan = plan(migration());
        assertThat(plan.unrecognized(), empty());
        assertThat(plan.segments(), hasSize(6));
        assertThat(plan.segments().stream().map(Plan.SegmentPlan::targetKey).toList(), hasItem(namespace + "/default"));
        assertThat(plan.segments().stream().map(Plan.SegmentPlan::targetKey).toList(), hasItem(namespace + "/bill/" + today));
        assertThat(plan.machines().records(), greaterThan(0));
        assertThat(plan.machines().maxTimestamp(), greaterThanOrEqualTo((Long) machines.get("maxTimestamp")));
        assertThat("M3", plan.layoutsOk(), is(true));

        ApplyResult result = migration().apply(apply(Duration.ofMillis(300), true));
        assertThat(result.w(), greaterThan((Long) machines.get("maxTimestamp")));
        assertThat(result.observedMillis(), greaterThanOrEqualTo(300L));
        assertThat(verify(), empty());

        try (TargetAdmin admin = Targets.open(target.location())) {
            assertThat(admin.range(namespace + "/bill/20240101").orElseThrow().next(), is(billPast + 1));
            assertThat(admin.range(namespace + "/bill/20250315").orElseThrow().next(), is(billMonth + 1));
            NamespaceState slots = admin.namespace(namespace).orElseThrow();
            assertThat(slots.slots(), is(1024));
            assertThat(slots.minNextFloor(), greaterThanOrEqualTo(result.w()));
        }

        try (CosIdRuntime runtime = runtime()) {
            // M1: every segment continues past what 4.x reserved.
            assertThat(runtime.generator("order").generate(), greaterThan(order));
            assertThat(runtime.generator("big").generate(), greaterThan(big));
            assertThat(runtime.generator("default").generate(), greaterThan(share));
            GroupedId bill = runtime.periodic("bill").generate();
            assertThat(bill.period(), is(today));
            assertThat(bill.value(), greaterThan(billToday));
            // M2 (+ M3, the same layout): every 5.0 snowflake id is after every 4.x one.
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                ids.add(runtime.generator("snow").generate());
            }
            assertThat(ids, everyItem(greaterThan(snowflakeMax)));
            assertThat(runtime.slot().orElseThrow().floor(), greaterThanOrEqualTo(result.w()));
        }
        // Still true once 5.0 ran: values only grew.
        assertThat(verify(), empty());
    }

    @Test
    void quietPeriodAbortsApplyAndTheSealKeepsLeftoverIdsApart() throws InterruptedException {
        writer.segment(namespace, "order", 0, 10, 3);
        plan(migration());
        String leftover = writer.startLeftover(namespace, "order", 10, 20);
        try {
            MigrationAbortedException aborted = assertThrows(MigrationAbortedException.class, () -> migration().apply(apply(Duration.ofMillis(400), true)));
            assertThat(aborted.getMessage(), containsString("segment [" + namespace + ".order]"));
            try (TargetAdmin admin = Targets.open(target.location())) {
                assertThat("nothing written", admin.range(namespace + "/order").isPresent(), is(false));
            }

            // The check turned off: the seal alone keeps the instance left running away from 5.0.
            ApplyResult result = migration().apply(apply(Duration.ZERO, true));
            long sealedAt = result.segments().get(0).sealedAt();
            assertThat(sealedAt - result.segments().get(0).lastMaxId(), is(Migration.DEFAULT_SEAL_GAP));
            Thread.sleep(200);
            List<Long> issued5 = new ArrayList<>();
            try (CosIdRuntime runtime = runtime()) {
                for (int i = 0; i < 3000; i++) {
                    issued5.add(runtime.generator("order").generate());
                }
            }
            List<Long> ranges4 = writer.stopLeftover(leftover);
            leftover = null;
            assertThat(ranges4.get(ranges4.size() - 1), greaterThan(sealedAt));
            for (long id : issued5) {
                for (long max : ranges4) {
                    if (id > max - 10 && id <= max) {
                        throw new AssertionError("5.0 id " + id + " is in the 4.x range (" + (max - 10) + ", " + max + "]");
                    }
                }
            }
            assertThat(verify(), hasItem(containsString("grew")));
        } finally {
            if (leftover != null) {
                writer.stopLeftover(leftover);
            }
        }
    }

    @Test
    void applyIsIdempotentAndResumesAfterAnInterruption() {
        long order = writer.segment(namespace, "order", 0, 10, 4);
        long past = writer.segment(namespace, "bill@20240101", 0, 5, 2);
        writer.machines(namespace, 1, 10);
        plan(migration());

        // Crash after the first seal.
        AtomicInteger seals = new AtomicInteger();
        Function<StoreLocation, SourceReader> crashingSource = location -> new FaultySource(Sources.open(location), () -> seals.incrementAndGet() > 1);
        assertThrows(IllegalStateException.class, () -> migration(crashingSource, Targets::open).apply(apply(Duration.ZERO, true)));
        // Crash after every seal, at the first target write.
        AtomicInteger raises = new AtomicInteger();
        Function<StoreLocation, TargetAdmin> crashingTarget = location -> new FaultyTarget(Targets.open(location), () -> raises.incrementAndGet() > 1);
        assertThrows(IllegalStateException.class, () -> migration(Sources::open, crashingTarget).apply(apply(Duration.ZERO, true)));

        ApplyResult first = migration().apply(apply(Duration.ZERO, true));
        Map<String, Long> lastMaxIds = new LinkedHashMap<>();
        first.segments().forEach(segment -> lastMaxIds.put(segment.sourceName(), segment.lastMaxId()));
        assertThat("sealed once, not once per run", lastMaxIds, is(Map.of("order", order, "bill@20240101", past)));
        assertThat(first.warnings(), hasItem(containsString("already sealed")));

        ApplyResult second = migration().apply(apply(Duration.ZERO, true));
        assertThat(second.segments(), is(first.segments()));
        try (SourceReader reader = Sources.open(source.location())) {
            SourceSnapshot snapshot = reader.read(namespace);
            assertThat(snapshot.segments().get("order"), is(order + Migration.DEFAULT_SEAL_GAP));
        }
        try (TargetAdmin admin = Targets.open(target.location())) {
            assertThat(admin.range(namespace + "/order").map(RangeState::next), is(Optional.of(order + 1)));
            assertThat(admin.namespace(namespace).orElseThrow().minNextFloor(), greaterThanOrEqualTo(second.w()));
        }
        assertThat(verify(), empty());
        assertThat(report.toString(StandardCharsets.UTF_8), not(containsString("FAIL")));
        assertThat(List.of(first.namespace()), contains(namespace));
    }

    /** Delegates, failing {@link #seal} once {@code fail} says so. */
    record FaultySource(SourceReader delegate, java.util.function.BooleanSupplier fail) implements SourceReader {
        @Override
        public SourceSnapshot read(String namespace) {
            return delegate.read(namespace);
        }

        @Override
        public long now() {
            return delegate.now();
        }

        @Override
        public long seal(String namespace, String name, long increment) {
            if (fail.getAsBoolean()) {
                throw new IllegalStateException("crash before sealing " + name);
            }
            return delegate.seal(namespace, name, increment);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    /** Delegates, failing {@link #raise} once {@code fail} says so. */
    record FaultyTarget(TargetAdmin delegate, java.util.function.BooleanSupplier fail) implements TargetAdmin {
        @Override
        public long now() {
            return delegate.now();
        }

        @Override
        public Optional<RangeState> range(String key) {
            return delegate.range(key);
        }

        @Override
        public RangeState raise(String key, long next) {
            if (fail.getAsBoolean()) {
                throw new IllegalStateException("crash before raising " + key);
            }
            return delegate.raise(key, next);
        }

        @Override
        public List<RangeState> ranges(String prefix) {
            return delegate.ranges(prefix);
        }

        @Override
        public boolean delete(String key) {
            return delegate.delete(key);
        }

        @Override
        public Optional<NamespaceState> namespace(String namespace) {
            return delegate.namespace(namespace);
        }

        @Override
        public NamespaceState prepare(String namespace, int limit, long toleranceMillis, int slotOffset, long nextFloor) {
            return delegate.prepare(namespace, limit, toleranceMillis, slotOffset, nextFloor);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
