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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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
        return migration().plan(new Migration.PlanOptions(source.location(), target.location(), machineFrom, machineTo, namespace, layout, null, null,
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
            source.location(), target.location(), namespace, null, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(),
            dir.resolve("plan.json"))));
        assertThat(refused.getMessage(), containsString("--layout-config"));
        Plan plan = migration().plan(new Migration.PlanOptions(source.location(), target.location(), namespace, null, null, null, Duration.ofSeconds(1),
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
        MigrationAbortedException noMachineTo = assertThrows(MigrationAbortedException.class, () -> plan(layout, machines.location(), null, Map.of()));
        assertThat(noMachineTo.getMessage(), containsString("--machine-to"));
        MigrationAbortedException wrongFrom = assertThrows(MigrationAbortedException.class, () -> migration().plan(new Migration.PlanOptions(
            machines.location(), target.location(), null, null, namespace, layout, null, null, Duration.ofSeconds(1), Duration.ZERO, Map.of(), Map.of(),
            dir.resolve("plan.json"))));
        assertThat(wrongFrom.getMessage(), containsString("--from is zookeeper"));

        Plan plan = plan(layout, machines.location(), machines.location(), Map.of());
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
            admin.prepare(namespace, 16, 1000, 0);
            store5.slots().acquire(namespace, new Owner("live", UUID.randomUUID()), 16, Duration.ofSeconds(30), Duration.ofSeconds(1), OptionalInt.empty());
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
            admin.prepare(namespace, 16, 1000, 0);
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
        Plan plan = migration().plan(new Migration.PlanOptions(source.location(), target.location(), namespace, layout(), target5, null,
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
