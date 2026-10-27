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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.target.TargetAdmin;
import me.ahoo.cosid.migration.target.Targets;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

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
        ApplyResult result = migration().apply(apply(Duration.ZERO, false));
        assertThat(result.segments().size(), is(1));
        assertThat(result.warnings(), hasItem(containsString("is not migrated")));
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
        assertThat(result.warnings(), hasItem(containsString("segment [late] is new since the plan")));
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
                  layout:
                    epoch: 2030-01-01T00:00:00Z
                    unit: MILLIS
                    time-bits: 41
                    node-bits: 10
                    seq-bits: 12
            """);
        Plan plan = migration().plan(new Migration.PlanOptions(source.location(), target.location(), namespace, resource("scenario-4x.yaml"), target5, null,
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
