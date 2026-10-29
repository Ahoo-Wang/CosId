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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.target.TargetAdmin;
import me.ahoo.cosid.migration.target.Targets;

import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.store.Store;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * What {@code plan} reports and {@code apply} refuses, on H2.
 */
class ApplyGuardsTest extends MigrationScenarioTest {
    @Override
    TestBackend sourceBackend() {
        return TestBackend.H2;
    }

    @Override
    void migratesRealDataAndIssuesUniqueIds() {
        // covered by H2MigrationTest
    }

    @Override
    void quietPeriodAbortsApplyAndTheSealKeepsLeftoverIdsApart() {
        // covered by H2MigrationTest
    }

    @Override
    void applyIsIdempotentAndResumesAfterAnInterruption() {
        // covered by H2MigrationTest
    }

    @Test
    void unrecognizedKeysAreListedNotMigrated() {
        writer.segment(namespace, "x/y", 0, 10, 1);
        writer.segment(namespace, "bill@26-10", 0, 10, 1);
        writer.segment(namespace, "order", 0, 10, 1);
        Plan plan = plan(migration());
        assertThat(plan.segments().size(), is(1));
        assertThat(plan.unrecognized(), hasItem(containsString(namespace + ".x/y")));
        assertThat(plan.unrecognized(), hasItem(containsString("--group-pattern bill=")));
        // They would start over in 5.0: apply refuses (rc-review D-B3) ...
        MigrationAbortedException refused = assertThrows(MigrationAbortedException.class, () -> migration().apply(apply(Duration.ZERO, false)));
        assertThat(refused.getMessage(), containsString(namespace + ".bill@26-10"));
        assertThat(refused.getMessage(), containsString("--allow-unmigrated"));
        // ... unless the operator names each one.
        ApplyResult result = migration().apply(allowing(namespace + ".x/y", namespace + ".bill@26-10"));
        assertThat(result.segments().size(), is(1));
        assertThat(result.allowedUnmigrated(), is(List.of(namespace + ".bill@26-10", namespace + ".x/y")));
        assertThat(result.warnings(), hasItem(containsString("is not migrated")));
        assertThat(verify(), empty());
    }

    private Migration.ApplyOptions allowing(String... keys) {
        Migration.ApplyOptions base = apply(Duration.ZERO, false);
        return new Migration.ApplyOptions(base.plan(), true, Duration.ZERO, 0, Duration.ZERO, false, Migration.DEFAULT_SEAL_GAP, base.result(), Map.of(),
            Map.of(), base.layoutConfig(), null, Set.of(keys), Map.of(), Map.of());
    }

    private Path layout(String yaml) throws IOException {
        Path file = dir.resolve("layout-" + UUID.randomUUID() + ".yaml");
        Files.writeString(file, yaml.replace("NS", namespace));
        return file;
    }

    private Plan plan(Path layout, StoreLocation machineFrom, StoreLocation machineTo, Map<String, String> renames) {
        return migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), machineFrom, machineTo, namespace, layout, null, null,
            Duration.ofSeconds(1), Duration.ZERO, renames, Map.of(), dir.resolve("plan.json")));
    }

    private Migration.ApplyOptions apply(Path layout) {
        return new Migration.ApplyOptions(dir.resolve("plan.json"), true, Duration.ZERO, 0, Duration.ZERO, true, Migration.DEFAULT_SEAL_GAP,
            dir.resolve("apply-result.json"), Map.of(), Map.of(), layout, null);
    }

    @Test
    void keysOfAnOwnNamespaceMigrateIntoTheNamespace() throws IOException {
        String own = namespace + "-legacy";
        long legacy = writer.segment(own, "legacy", 0, 10, 3);
        long order = writer.segment(namespace, "order", 0, 10, 1);
        Path layout = layout("""
            cosid:
              namespace: NS
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider:
                  order: {}
                  legacy: { namespace: NS-legacy }
            """);
        Plan plan = plan(layout, null, null, Map.of());
        assertThat(plan.extraNamespaces(), is(Map.of(own, List.of("legacy"))));
        assertThat(plan.segments().stream().map(s -> s.sourceNamespace() + "." + s.sourceName() + " -> " + s.targetKey()).toList(),
            is(List.of(namespace + ".order -> " + namespace + "/order", own + ".legacy -> " + namespace + "/legacy")));
        ApplyResult result = migration().apply(apply(layout));
        try (TargetAdmin admin = Targets.open(target.location())) {
            assertThat(admin.range(namespace + "/legacy").orElseThrow().next(), is(legacy + 1));
            assertThat(admin.range(namespace + "/order").orElseThrow().next(), is(order + 1));
        }
        assertThat(result.segments().stream().filter(s -> s.sourceNamespace().equals(own)).findFirst().orElseThrow().sealedAt(),
            is(legacy + Migration.DEFAULT_SEAL_GAP));
        assertThat(verify(), empty());
        // A 4.x instance of the own namespace that keeps going is seen too.
        writer.segment(own, "legacy", 0, 10, 1);
        assertThat(verify(), hasItem(containsString(own + ".legacy] grew")));
    }

    @Test
    void aDottedNamespaceKeepsItsKeys() throws IOException {
        writer.segment(namespace + ".v2", "order", 0, 10, 2);
        writer.segment(namespace, "bill", 0, 10, 1);
        Path layout = layout("""
            cosid:
              namespace: NS
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider:
                  bill: {}
                  order: { namespace: NS.v2 }
            """);
        Plan plan = plan(layout, null, null, Map.of());
        // "NS.v2.order" belongs to namespace NS.v2, not to NS as segment "v2.order" (rc-review D-N1).
        assertThat(plan.segments().stream().map(s -> s.sourceNamespace() + "|" + s.sourceName()).toList(),
            is(List.of(namespace + "|bill", namespace + ".v2|order")));
    }

    @Test
    void theShareNeedsTheLayoutConfig() {
        writer.segment(namespace, "__share__", 0, 10, 1);
        MigrationAbortedException refused = assertThrows(MigrationAbortedException.class, () -> migration().plan(new Migration.PlanOptions(
            source.sourceLocation(), target.location(), namespace, null, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(),
            dir.resolve("plan.json"))));
        assertThat(refused.getMessage(), containsString("--layout-config"));
        Plan plan = migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), namespace, null, null, null, Duration.ofSeconds(1),
            Duration.ZERO, Map.of("__share__", "segment-share"), Map.of(), dir.resolve("plan.json")));
        assertThat(plan.segments().get(0).targetKey(), is(namespace + "/segment-share"));
    }

    @Test
    void separateMachineStoresAreFollowed() throws IOException {
        TestBackend.Store machines = TestBackend.ZOOKEEPER.store();
        Map<String, Object> written;
        try (Legacy4xWriter zookeeper = machines.writer()) {
            written = zookeeper.machines(namespace, 2, 100);
        }
        writer.segment(namespace, "order", 0, 10, 1);
        Path layout = layout("""
            cosid:
              namespace: NS
              machine: { enabled: true, distributor: { type: zookeeper } }
              snowflake: { enabled: true, share: { enabled: true } }
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider: { order: {} }
            """);
        MigrationAbortedException noMachineFrom = assertThrows(MigrationAbortedException.class, () -> plan(layout, null, null, Map.of()));
        assertThat(noMachineFrom.getMessage(), containsString("--machine-from"));
        MigrationAbortedException noMachineTo = assertThrows(MigrationAbortedException.class, () -> plan(layout, machines.sourceLocation(), null, Map.of()));
        assertThat(noMachineTo.getMessage(), containsString("--machine-to"));
        MigrationAbortedException wrongFrom = assertThrows(MigrationAbortedException.class, () -> migration().plan(new Migration.PlanOptions(
            machines.sourceLocation(), target.location(), null, null, namespace, layout, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(),
            dir.resolve("plan.json"))));
        assertThat(wrongFrom.getMessage(), containsString("--from is zookeeper"));

        Plan plan = plan(layout, machines.sourceLocation(), machines.location(), Map.of());
        assertThat("T4 comes from the machine store", plan.machines().maxTimestamp(), greaterThanOrEqualTo((Long) written.get("maxTimestamp")));
        ApplyResult result = migration().apply(apply(layout));
        try (TargetAdmin slots = Targets.open(machines.location()); TargetAdmin ranges = Targets.open(target.location())) {
            assertThat(slots.namespace(namespace).orElseThrow().minNextFloor(), greaterThanOrEqualTo(result.w()));
            assertThat(ranges.namespace(namespace).isPresent(), is(false));
        }
        assertThat(result.machineTarget().type(), is("zookeeper"));
        assertThat(verify(), empty());
    }

    @Test
    void verifyFindsKeysCreatedAfterApply() {
        writer.segment(namespace, "order", 0, 10, 1);
        plan(migration());
        migration().apply(apply(Duration.ZERO, true));
        assertThat(verify(), empty());
        writer.segment(namespace, "bill@20991231", 0, 10, 1);
        assertThat(verify(), hasItem(containsString(namespace + ".bill@20991231] is not in the result")));
    }

    @Test
    void purgeRefusesALiveNamespace() {
        try (TargetAdmin admin = Targets.open(target.location()); Store store5 = target.open5()) {
            admin.prepare(namespace, 16, 1000, 0, 0);
            store5.slots().acquire(namespace, new Owner("live", UUID.randomUUID()), 16, Duration.ofSeconds(30), Duration.ofSeconds(1), 0, OptionalInt.empty());
        }
        MigrationAbortedException refused = assertThrows(MigrationAbortedException.class,
            () -> migration().purge(new Migration.PurgeOptions(target.location(), namespace, "20250101", true, false)));
        assertThat(refused.getMessage(), containsString("live slot lease"));
        assertThat(migration().purge(new Migration.PurgeOptions(target.location(), namespace, "20250101", false, true)), empty());
    }

    @Test
    void configContinuesTheMigratedKeys() throws IOException {
        writer.segment(namespace, "order", 0, 10, 1);
        Path layout = layout("""
            cosid:
              namespace: NS
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider: { order: {}, fresh: {} }
            """);
        plan(layout, null, null, Map.of("order", "orders"));
        migration().apply(apply(layout));
        Path output = dir.resolve("application-5.yaml");
        Path result = dir.resolve("apply-result.json");
        // "fresh" never issued in 4.x: no key, so config wants it named (rc-review D-B2).
        MigrationAbortedException refused = assertThrows(MigrationAbortedException.class,
            () -> migration().config(new Migration.ConfigOptions(layout, result, output, ZoneId.of("UTC"))));
        assertThat(refused.getMessage(), containsString("[fresh]"));
        assertThrows(MigrationAbortedException.class, () -> migration().config(new Migration.ConfigOptions(layout, null, output, ZoneId.of("UTC"))));
        migration().config(new Migration.ConfigOptions(layout, result, output, ZoneId.of("UTC"), Set.of("fresh")));
        // The rename of the plan names the generator: it continues the key "orders".
        assertThat(Files.readString(output), containsString("    orders:\n      strategy: segment"));
    }

    private static final String GENERATOR_LAYOUT = """
        cosid:
          namespace: NS
          machine: { enabled: true, distributor: { type: jdbc } }
          generator: { enabled: ENABLED, namespace: GENERATOR_NS }
          segment:
            enabled: true
            share: { enabled: false }
            distributor: { type: jdbc }
            provider: { order: {} }
        """;

    private Path generatorLayout(boolean enabled, String generatorNamespace) throws IOException {
        return layout(GENERATOR_LAYOUT.replace("ENABLED", String.valueOf(enabled)).replace("GENERATOR_NS", generatorNamespace));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSharedGeneratorNamespaceMigratesOnceAsANamedMachine() throws IOException {
        // D37, api-freeze.md §2.7: 4.x shares the CosIdGenerator machine ids of "global" across services; 5.0 keeps them
        // in the named machine "generator", its slots at the top of the 2^20 nodes, so 4.x may keep running below them.
        String global = "global-" + namespace;
        writer.segment(namespace, "order", 0, 10, 1);
        Map<String, Object> written = writer.machines(global, 3, 10);
        int maxMachineId = ((List<Number>) written.get("machineIds")).stream().mapToInt(Number::intValue).max().orElseThrow();
        int offset = (1 << 20) - 1024;
        Path layout = generatorLayout(true, global);

        // A service before its shared namespace: plan says so, apply refuses.
        Plan early = plan(layout, null, null, Map.of());
        assertThat(early.generatorNamespace().prepared(), is(false));
        assertThat(early.warnings(), hasItem(containsString("is not migrated yet")));
        MigrationAbortedException notYet = assertThrows(MigrationAbortedException.class, () -> migration().apply(apply(layout)));
        assertThat(notYet.getMessage(), containsString("[" + global + "] is not migrated"));

        // 1. The shared namespace, once: machine records only, the slot offset above every 4.x machine id.
        Path sharedPlan = dir.resolve("shared-plan.json");
        Path sharedResult = dir.resolve("shared-result.json");
        Plan shared = migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), null, null, global, layout, null, null,
            Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), sharedPlan));
        assertThat(shared.segments(), empty());
        long headroom = offset - (maxMachineId + 1L);
        assertThat(shared.shared(), is(new Plan.Shared(maxMachineId, offset, 20, true, 1L << 20, headroom, false)));
        assertThat(shared.slots().slotOffset(), is(offset));
        assertThat(shared.warnings(), hasItem(containsString("4.x can hand out " + headroom + " more machine ids")));
        assertThat(shared.warnings(), not(hasItem(containsString("LOW HEADROOM"))));
        String planJson = Files.readString(sharedPlan);
        assertThat(planJson, containsString("\"headroom\" : " + headroom));
        assertThat(planJson, containsString("\"guaranteed\" : false"));
        // Rolling: neither --confirm-stopped nor a quiet period; running it again changes nothing.
        Migration.ApplyOptions rolling = new Migration.ApplyOptions(sharedPlan, false, Duration.ofMinutes(5), 0, Duration.ZERO, true,
            Migration.DEFAULT_SEAL_GAP, sharedResult, Map.of(), Map.of(), layout, null);
        ApplyResult sharedApplied = migration().apply(rolling);
        assertThat(sharedApplied.observedMillis(), is(0L));
        assertThat(sharedApplied.slots().slotOffset(), is(offset));
        assertThat(migration().apply(rolling).slots().slotOffset(), is(offset));
        assertThat(migration().verify(new Migration.VerifyOptions(sharedPlan, sharedResult, Map.of(), Map.of())), empty());

        // 2. The service: its shared namespace is migrated, rolling, so its 4.x records count for nothing here.
        Plan service = plan(layout, null, null, Map.of());
        assertThat(service.generatorNamespace(), is(new Plan.GeneratorNamespace(global, 1024, 1000, offset)));
        assertThat(service.extraNamespaces().containsKey(global), is(false));
        ApplyResult result = migration().apply(apply(layout));
        assertThat(result.generatorNamespace().slotOffset(), is(offset));
        assertThat(verify(), empty());

        // 3. config: the CosIdGenerator issues on the named machine of the shared namespace.
        Path output = dir.resolve("application-5.yaml");
        migration().config(new Migration.ConfigOptions(layout, dir.resolve("apply-result.json"), output, ZoneId.of("UTC"), Set.of()));
        String yaml = Files.readString(output);
        assertThat(yaml, containsString("  machines:\n    generator:\n      namespace: " + global + "\n      leased:\n"));
        assertThat(yaml, containsString("        slot-offset: " + offset + "\n"));
        assertThat(yaml, containsString("      strategy: cosid80\n      machine: generator\n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSlotOffsetOfASharedNamespaceIsChecked() throws IOException {
        String global = "global-" + namespace;
        Map<String, Object> written = writer.machines(global, 4, 10);
        int maxMachineId = ((List<Number>) written.get("machineIds")).stream().mapToInt(Number::intValue).max().orElseThrow();
        Path layout = generatorLayout(true, global);
        Path planned = dir.resolve("shared-plan.json");
        // The default: the top of the 2^20 nodes (cosid.generator.machine-bit), below the 4.x hard cap 2^20, so not guaranteed.
        Plan top = migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), null, null, global, layout, null, null,
            Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), planned));
        assertThat(top.slots().slotOffset(), is((1 << 20) - 1024));
        assertThat(top.shared().headroom(), is((1L << 20) - 1024 - (maxMachineId + 1)));
        assertThat(top.shared().guaranteed(), is(false));
        // M + 1 a power of two: M + 1 is the least offset, with no headroom at all; M is refused.
        assertThat(Integer.bitCount(maxMachineId + 1), is(1));
        Plan least = migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), null, null, global, layout, null, null,
            Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), planned, maxMachineId + 1));
        assertThat(least.slots().slotOffset(), is(maxMachineId + 1));
        assertThat(least.shared().headroom(), is(0L));
        assertThat(least.shared().guaranteed(), is(false));
        assertThat(least.warnings(), hasItem(startsWith("LOW HEADROOM: [" + global + "]")));
        // Not above the 4.x machine ids: their nodes would collide.
        MigrationAbortedException low = assertThrows(MigrationAbortedException.class, () -> migration().plan(new Migration.PlanOptions(
            source.sourceLocation(), target.location(), null, null, global, layout, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(),
            planned, maxMachineId)));
        assertThat(low.getMessage(), containsString("is not above the 4.x machine ids"));
        // Beyond the node bits of the CosIdGenerator: 2^20 nodes hold no 2^10 slots above 2^20 - 1.
        MigrationAbortedException high = assertThrows(MigrationAbortedException.class, () -> migration().plan(new Migration.PlanOptions(
            source.sourceLocation(), target.location(), null, null, global, layout, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(),
            planned, (1 << 20) - 512)));
        assertThat(high.getMessage(), containsString("exceeds the 2^20 nodes"));
        assertThat(high.getMessage(), containsString("--slot-bits 9"));
        // Offset 0: migrated at once, every service stopped, so apply wants --confirm-stopped.
        Plan atOnce = migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), null, null, global, layout, null, null,
            Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), planned, 0));
        assertThat(atOnce.shared().rolling(), is(false));
        assertThat(atOnce.shared().guaranteed(), is(true));
        assertThrows(MigrationAbortedException.class, () -> migration().apply(new Migration.ApplyOptions(planned, false, Duration.ZERO, 0,
            Duration.ZERO, true, Migration.DEFAULT_SEAL_GAP, dir.resolve("shared-result.json"), Map.of(), Map.of(), layout, null)));
        // --slot-offset belongs to the shared namespace only.
        assertThrows(MigrationAbortedException.class, () -> migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), null,
            null, namespace, layout, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), planned, 4)));
    }

    @Test
    void cosIdGeneratorBitsBeyondALongAreRefused() throws IOException {
        // timeBits + seqBits <= 63 (D36): refused before a configuration 5.0 would not start.
        writer.segment(namespace, "order", 0, 10, 1);
        Path wide = layout(GENERATOR_LAYOUT.replace("ENABLED", "true").replace(", namespace: GENERATOR_NS", ", timestamp-bit: 50, sequence-bit: 16"));
        MigrationAbortedException planned = assertThrows(MigrationAbortedException.class, () -> plan(wide, null, null, Map.of()));
        assertThat(planned.getMessage(), containsString("= 66 exceeds 63"));
        Path output = dir.resolve("application-5.yaml");
        assertThrows(MigrationAbortedException.class, () -> migration().config(new Migration.ConfigOptions(wide, null, output, ZoneId.of("UTC"),
            Set.of("order"))));
        assertThat(Files.exists(output), is(false));
    }

    @Test
    void aCosIdGeneratorOfTheServiceNamespaceConverts() throws IOException {
        writer.segment(namespace, "order", 0, 10, 1);
        Path output = dir.resolve("application-5.yaml");
        // The same namespace as cosid.namespace, blank (4.x takes cosid.namespace then), or no generator namespace at all.
        for (String generatorNamespace : List.of(namespace, "\"\"")) {
            Path layout = generatorLayout(true, generatorNamespace);
            assertThat(plan(layout, null, null, Map.of()).extraNamespaces(), is(Map.of()));
            migration().config(new Migration.ConfigOptions(layout, null, output, ZoneId.of("UTC"), Set.of("order")));
            assertThat(Files.readString(output), containsString("    cosid:\n      strategy: cosid80"));
            assertThat(Files.readString(output), not(containsString("cosid.generator.namespace")));
        }
        Path unset = layout(GENERATOR_LAYOUT.replace(", namespace: GENERATOR_NS", "").replace("ENABLED", "true"));
        migration().config(new Migration.ConfigOptions(unset, null, output, ZoneId.of("UTC"), Set.of("order")));
        assertThat(Files.readString(output), containsString("strategy: cosid80"));
    }

    @Test
    void theMachineRecordsOfTheGeneratorNamespaceCountForT4() throws IOException {
        // A disabled CosIdGenerator does not stop the migration, but the records 4.x left in its namespace count for W.
        String global = "global-" + namespace;
        writer.segment(namespace, "order", 0, 10, 1);
        Map<String, Object> written = writer.machines(global, 2, 100);
        Path layout = generatorLayout(false, global);
        Plan plan = plan(layout, null, null, Map.of());
        assertThat(plan.extraNamespaces(), is(Map.of(global, List.of())));
        assertThat(plan.machines().records(), greaterThanOrEqualTo(1));
        assertThat(plan.machines().maxTimestamp(), greaterThanOrEqualTo((Long) written.get("maxTimestamp")));
        assertThat(plan.warnings(), hasItem(containsString("cosid.generator.namespace = " + global)));
        ApplyResult result = migration().apply(apply(layout));
        assertThat(result.w(), greaterThan((Long) written.get("maxTimestamp")));
        assertThat(result.segments().size(), is(1));
        assertThat(verify(), empty());
    }

    @Test
    void withoutTheSealTheValuesAreTakenAsTheyAre() {
        long order = writer.segment(namespace, "order", 0, 10, 2);
        plan(migration());
        writer.segment(namespace, "late", 0, 10, 1);
        ApplyResult result = migration().apply(apply(Duration.ZERO, false));
        assertThat(result.sealed(), is(false));
        ApplyResult.SegmentResult segment = result.segments().stream().filter(s -> s.sourceName().equals("order")).findFirst().orElseThrow();
        assertThat(segment.lastMaxId(), is(order));
        assertThat(segment.sealedAt(), nullValue());
        assertThat(result.warnings(), hasItem(containsString("segment [" + namespace + ".late] is new since the plan")));
        assertThat(result.warnings(), hasItem(containsString("--observe 0")));
        assertThat(verify(), empty());
    }

    @Test
    void aSegmentGoneSinceThePlanAborts() throws SQLException {
        writer.segment(namespace, "order", 0, 10, 1);
        plan(migration());
        try (Connection connection = DriverManager.getConnection(source.url()); Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from cosid where name = '" + namespace + ".order'");
        }
        MigrationAbortedException aborted = assertThrows(MigrationAbortedException.class, () -> migration().apply(apply(Duration.ZERO, true)));
        assertThat(aborted.getMessage(), containsString("is gone"));
    }

    @Test
    void anotherSlotLimitInTheTargetFails() {
        writer.segment(namespace, "order", 0, 10, 1);
        try (TargetAdmin admin = Targets.open(target.location())) {
            admin.prepare(namespace, 16, 1000, 0, 0);
        }
        Plan plan = plan(migration());
        assertThat(plan.warnings(), hasItem(containsString("recorded with limit 16")));
        IllegalStateException failed = assertThrows(IllegalStateException.class, () -> migration().apply(apply(Duration.ZERO, true)));
        assertThat(failed.getMessage(), containsString("--slot-bits"));
    }

    @Test
    void aFailedLayoutValidationStopsApply() throws IOException {
        writer.segment(namespace, "order", 0, 10, 1);
        Path target5 = dir.resolve("application-5.yaml");
        Files.writeString(target5, """
            cosid:
              generators:
                snow:
                  strategy: snowflake
                  layout: SNOWFLAKE_MS
                  epoch: 2030-01-01T00:00:00Z
            """);
        Plan plan = migration().plan(new Migration.PlanOptions(source.sourceLocation(), target.location(), namespace, layout(), target5, null,
            Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(), dir.resolve("plan.json")));
        assertThat(plan.layoutsOk(), is(false));
        MigrationAbortedException aborted = assertThrows(MigrationAbortedException.class, () -> migration().apply(apply(Duration.ZERO, true)));
        assertThat(aborted.getMessage(), containsString("snow"));
        assertThrows(MigrationAbortedException.class, () -> migration().apply(new Migration.ApplyOptions(dir.resolve("plan.json"), false, Duration.ZERO, 0,
            Duration.ZERO, true, 1, dir.resolve("r.json"), Map.of(), Map.of(), null, null)));
    }

    @Test
    void verifyFindsWhatIsMissing() throws IOException {
        writer.segment(namespace, "order", 0, 10, 1);
        plan(migration());
        migration().apply(apply(Duration.ZERO, true));
        // Another plan file than the one applied.
        Path plan = dir.resolve("plan.json");
        Files.writeString(plan, Files.readString(plan) + "\n");
        assertThat(String.join("\n", verify()), containsString("another plan"));
    }
}
