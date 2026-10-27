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
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The command line, end to end on H2: plan, apply, verify, config and purge as an operator runs them.
 */
class MainTest {
    @TempDir
    Path dir;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        out.reset();
        err.reset();
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void usageAndErrors() {
        assertThat(run(), is(2));
        assertThat(out(), containsString("Usage: java -jar cosid-migration.jar"));
        assertThat(run("--help"), is(0));
        assertThat(run("rollback"), is(2));
        assertThat(err(), containsString("Unknown command [rollback]"));
        assertThat(run("plan", "--to", "jdbc:h2:mem:x", "--namespace", "app"), is(2));
        assertThat(err(), containsString("--from is required"));
        assertThat(run("verify", "--nope"), is(2));
        assertThat(run("apply", "--plan", dir.resolve("missing.json").toString(), "--confirm-stopped"), is(1));
        assertThat(err(), containsString("failed"));
        assertThat(run("purge", "--to", "jdbc:h2:mem:x", "--namespace", "app", "--before", "2025"), is(1));
        assertThat(err(), containsString("--confirm-stopped"));
    }

    @Test
    void anOperatorRunsTheMigration() throws IOException {
        TestBackend.Store store = TestBackend.H2.store();
        String namespace = "cli" + UUID.randomUUID().toString().substring(0, 8);
        long order;
        try (Legacy4xWriter writer = store.writer()) {
            order = writer.segment(namespace, "order", 0, 10, 3);
            writer.segment(namespace, "bill@20240101", 0, 10, 1);
            writer.machines(namespace, 1, 10);
        }
        String plan = dir.resolve("plan.json").toString();
        String result = dir.resolve("apply-result.json").toString();
        String layout = MigrationScenarioTest.resource("scenario-4x.yaml").toString();
        assertThat(run("plan", "--from", store.url(), "--to", store.url(), "--to-option", "initSchema=true", "--namespace", namespace,
            "--output", plan, "--layout-config", layout, "--source-skew", "0", "--slot-bits", "8", "--tolerance", "500ms"), is(0));
        assertThat(out(), containsString(namespace + "/order next >= " + (order + 1)));
        assertThat(Files.readString(Path.of(plan)), containsString("\"format\" : \"cosid-migration-plan/1\""));

        assertThat(run("apply", "--plan", plan, "--result", result), is(1));
        assertThat(err(), containsString("--confirm-stopped"));
        assertThat(run("apply", "--plan", plan, "--confirm-stopped", "--observe", "0", "--source-skew", "0", "--gap", "100", "--result", result,
            "--layout-config", layout), is(0));
        assertThat(out(), containsString(namespace + "/order next = " + (order + 101)));
        assertThat(run("verify", "--plan", plan, "--result", result), is(0));
        assertThat(out(), containsString("M1-M3 hold"));

        String output = dir.resolve("application-5.yaml").toString();
        assertThat(run("config", "--input", layout, "--result", result, "--output", output, "--zone", "UTC"), is(0));
        assertThat(Files.readString(Path.of(output)), containsString("strategy: periodic-segment"));

        assertThat(run("purge", "--to", store.url(), "--namespace", namespace, "--before", "20250101", "--dry-run"), is(0));
        assertThat(out(), containsString("kept " + namespace + "/bill/20240101: not sealed"));

        // A 4.x instance started after the migration: verify sees its machine record move.
        try (Legacy4xWriter writer = store.writer()) {
            writer.machines(namespace, 1, 10);
        }
        assertThat(run("verify", "--plan", plan, "--result", result), is(1));
        assertThat(out(), containsString("a 4.x instance still runs"));
    }
}
