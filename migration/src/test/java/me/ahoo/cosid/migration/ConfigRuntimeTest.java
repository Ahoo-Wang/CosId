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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.runtime.CosIdRuntime;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code config}'s output as a 5.0 application starts from it (ga-review §10, WP-migration-r2): 4.x on ZooKeeper,
 * migrated to H2, the YAML bound by Spring's binding and a {@link CosIdRuntime} started on the client the application
 * has for each store type, a {@code DataSource} on the H2 target here.
 */
class ConfigRuntimeTest extends MigrationScenarioTest {
    @Override
    TestBackend sourceBackend() {
        return TestBackend.ZOOKEEPER;
    }

    @Override
    TestBackend targetBackend() {
        return TestBackend.H2;
    }

    @Override
    void migratesRealDataAndIssuesUniqueIds() {
        // covered by ZookeeperToH2MigrationTest
    }

    @Override
    void quietPeriodAbortsApplyAndTheSealKeepsLeftoverIdsApart() {
        // covered by ZookeeperToH2MigrationTest
    }

    @Override
    void applyIsIdempotentAndResumesAfterAnInterruption() {
        // covered by ZookeeperToH2MigrationTest
    }

    private Path layout(String yaml) throws IOException {
        Path file = dir.resolve("layout-" + UUID.randomUUID() + ".yaml");
        Files.writeString(file, yaml.replace("NS", namespace).replace("ZK", source.url().substring("zookeeper://".length())));
        return file;
    }

    private Plan plan(Path layout, StoreLocation to, String planned, Path output) {
        return migration().plan(new Migration.PlanOptions(source.sourceLocation(), to, null, null, planned, layout, null, null, Duration.ofSeconds(1),
            Duration.ZERO, Map.of(), Map.of(), output));
    }

    private ApplyResult apply(Path plan, Path layout, Path result) {
        return migration().apply(new Migration.ApplyOptions(plan, true, Duration.ZERO, 0, Duration.ZERO, true, Migration.DEFAULT_SEAL_GAP, result,
            Map.of(), Map.of(), layout, null));
    }

    private Path config(Path layout, Path result, Set<String> allowNew) {
        Path output = dir.resolve("application-5-" + UUID.randomUUID() + ".yaml");
        migration().config(new Migration.ConfigOptions(layout, result, output, ZoneId.of("UTC"), allowNew));
        return output;
    }

    /** The runtime a 5.0 application starts from the YAML: its stores of type jdbc on its DataSource bean, the H2 target. */
    private CosIdRuntime start(Path yaml) throws IOException {
        RuntimeSpec spec = ConfigBindingTest.specOf(yaml);
        RuntimeOptions.Builder options = RuntimeOptions.defaults().toBuilder();
        spec.stores().forEach((name, store) -> {
            if (StoreLocation.JDBC.equals(store.type())) {
                JdbcDataSource dataSource = new JdbcDataSource();
                dataSource.setURL(target.url());
                options.client(name, dataSource);
            }
        });
        return CosIdRuntime.start(spec, options.build());
    }

    private static final String SERVICE = """
        cosid:
          namespace: NS
          zookeeper: { connect-string: ZK }
          machine: { enabled: true, distributor: { type: zookeeper } }
          snowflake: { enabled: true }
          segment:
            enabled: true
            share: { enabled: false }
            distributor: { type: zookeeper }
            provider: { order: { step: 10 } }
        """;

    @Test
    void aMigrationToAnotherStoreTypeStartsWhereApplyWrote() throws IOException {
        // R2-mig-01 and R2-mig-02: 4.x on ZooKeeper, 5.0 on H2 in tables of its own. config writes the store apply wrote to,
        // with its tables; before, it wrote a zookeeper store, and 5.0 issued from 1 on a store without the migrated keys.
        long order = writer.segment(namespace, "order", 0, 10, 5);
        Map<String, Object> machines = writer.machines(namespace, 2, 200);
        long snowflakeMax = (Long) machines.get("maxId");
        Path layout = layout(SERVICE);
        Path planFile = dir.resolve("plan.json");
        // A misspelt option is refused instead of leaving the data in the default table.
        IllegalArgumentException misspelt = assertThrows(IllegalArgumentException.class, () -> plan(layout,
            StoreLocation.parse(target.url(), Map.of("initSchema", "true", "rangeTabel", "ids_range")), namespace, planFile));
        assertThat(misspelt.getMessage(), containsString("unknown option [rangeTabel]"));
        // Kebab-case, as the 5.0 store takes it too.
        StoreLocation to = StoreLocation.parse(target.url(), Map.of("init-schema", "true", "range-table", "ids_range", "slot-table", "ids_slot",
            "namespace-table", "ids_namespace"));
        Plan plan = plan(layout, to, namespace, planFile);
        assertThat(plan.target().option("rangeTable"), is("ids_range"));
        ApplyResult result = apply(planFile, layout, dir.resolve("apply-result.json"));

        Path yaml = config(layout, dir.resolve("apply-result.json"), Set.of());
        String text = Files.readString(yaml);
        assertThat(text, containsString("  stores:\n    jdbc:\n      type: jdbc\n"));
        assertThat(text, containsString("        rangeTable: ids_range\n"));
        assertThat(text, containsString("        slotTable: ids_slot\n"));
        assertThat(text, containsString("        namespaceTable: ids_namespace\n"));
        assertThat(text, not(containsString("type: zookeeper")));
        assertThat(text, not(containsString("initSchema")));
        assertThat(text, containsString("cosid.segment.distributor.type = zookeeper: apply wrote to jdbc"));
        assertThat(text, containsString("cosid.zookeeper.connect-string = "));
        long next = result.segments().stream().filter(segment -> segment.targetKey().equals(namespace + "/order")).findFirst().orElseThrow().targetNext();
        assertThat(text, containsString("      initial-offset: " + next + "\n"));
        // Should the application open another store, the key starts there, past every 4.x id.
        assertThat(((GeneratorSpec.Segment) ConfigBindingTest.specOf(yaml).generators().get("order")).batch().initialOffset(), is(next));

        try (CosIdRuntime runtime = start(yaml)) {
            assertThat(runtime.generator("order").generate(), greaterThan(order));
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(runtime.generator("default").generate());
            }
            assertThat(ids, everyItem(greaterThan(snowflakeMax)));
            assertThat(runtime.slot().orElseThrow().floor(), greaterThanOrEqualTo(result.w()));
        }
    }

    @Test
    void aSnowflakeOfItsOwnNamespaceIssuesOnItsNamedMachine() throws IOException {
        // R2-parity-01: 4.x distributed the machine id of "trace" in a namespace of its own, which services may share. 5.0
        // has the named machine of it (D37), migrated once on its own like the shared cosid.generator.namespace (§2.7).
        String own = "own-" + namespace;
        Map<String, Object> machines = writer.machines(own, 3, 200);
        long snowflakeMax = (Long) machines.get("maxId");
        Path layout = layout("""
            cosid:
              namespace: NS
              zookeeper: { connect-string: ZK }
              machine: { enabled: true, distributor: { type: zookeeper } }
              snowflake:
                enabled: true
                share: { enabled: false }
                provider:
                  trace: { namespace: OWN }
            """.replace("OWN", own));
        StoreLocation to = target.location();
        Path planFile = dir.resolve("plan.json");
        Path resultFile = dir.resolve("apply-result.json");

        // The service before its namespace of its own: plan says so, apply refuses.
        Plan early = plan(layout, to, namespace, planFile);
        assertThat(early.machineNamespaces().get(own).prepared(), is(false));
        assertThat(early.warnings(), hasItem(containsString("cosid.snowflake.provider.trace.namespace = " + own + " is not migrated yet")));
        MigrationAbortedException notYet = assertThrows(MigrationAbortedException.class, () -> apply(planFile, layout, resultFile));
        assertThat(notYet.getMessage(), containsString("[" + own + "] is not migrated"));

        // 1. The namespace, once: its 10 slot bits fill the 10 node bits of the snowflake, so it migrates at once.
        Path ownPlan = dir.resolve("own-plan.json");
        Plan shared = plan(layout, to, own, ownPlan);
        assertThat(shared.segments().isEmpty(), is(true));
        assertThat(shared.slots().bits(), is(10));
        assertThat(shared.slots().slotOffset(), is(0));
        assertThat(shared.shared().rolling(), is(false));
        assertThat(shared.warnings(), hasItem(containsString("is the namespace of its own of cosid.snowflake.provider.trace")));
        ApplyResult ownApplied = apply(ownPlan, layout, dir.resolve("own-result.json"));
        assertThat(ownApplied.w(), greaterThan((Long) machines.get("maxTimestamp")));

        // 2. The service.
        plan(layout, to, namespace, planFile);
        ApplyResult result = apply(planFile, layout, resultFile);
        assertThat(result.machineNamespaces().get(own), is(new Plan.GeneratorNamespace(own, 1024, 1000, 0)));
        assertThat(verify(), is(List.of()));

        // 3. config: the snowflake issues on the named machine of its namespace.
        Path yaml = config(layout, resultFile, Set.of());
        String text = Files.readString(yaml);
        assertThat(text, containsString("  machines:\n    " + own + ":\n      namespace: " + own + "\n      leased:\n        store: jdbc\n        bits: 10\n"));
        assertThat(text, containsString("    trace:\n      strategy: snowflake\n      machine: " + own + "\n"));
        assertThat(text, containsString("written as the named machine cosid.machines." + own));
        try (CosIdRuntime runtime = start(yaml)) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(runtime.generator("trace").generate());
            }
            assertThat(ids, everyItem(greaterThan(snowflakeMax)));
            SlotLease lease = runtime.slot(own).orElseThrow();
            assertThat(lease.namespace(), is(own));
            assertThat(lease.floor(), greaterThanOrEqualTo(ownApplied.w()));
        }
    }

    @Test
    void theZookeeperRetryAndTheFriendlyTextOf4xKeepTheirMeaning() throws IOException {
        // R2-mig-04: the Curator retry of 4.x as the 5.0 options name it (the 4.x keys would fail the start); R2-parity-03:
        // the friendly text of a 4.x snowflake stays unpadded, which the 5.0 codec is not by default.
        Path layout = layout("""
            cosid:
              namespace: NS
              zookeeper:
                connect-string: ZK
                session-timeout: 30s
                retry: { base-sleep-time-ms: 50, max-retries: 2, max-sleep-ms: 200 }
              machine: { enabled: true, distributor: { type: manual, manual: { machine-id: 7 } } }
              snowflake:
                enabled: true
                share: { enabled: false }
                zone-id: UTC
                provider:
                  trace: { converter: { type: snowflake_friendly } }
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: zookeeper }
                provider: { order: {} }
            """);
        Path yaml = config(layout, null, Set.of("order"));
        String text = Files.readString(yaml);
        assertThat(text, containsString("        retry.baseSleepTime: 50ms\n        retry.maxRetries: 2\n        retry.maxSleep: 200ms\n"));
        assertThat(text, not(containsString("by hand")));
        assertThat(text, containsString("      - friendly:\n          zone: UTC\n          pad-start: false\n"));
        try (CosIdRuntime runtime = start(yaml)) {
            assertThat(runtime.generator("order").generate(), greaterThanOrEqualTo(1L));
            // <time>-7-<sequence>, as 4.x wrote it; padded it would be <time>-0007-0000.
            assertThat(runtime.text("trace").get(), matchesPattern("\\d+-7-\\d+"));
        }
    }
}
