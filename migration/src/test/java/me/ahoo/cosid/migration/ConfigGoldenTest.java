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
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Set;

/**
 * {@code config} goldens (09 §3): each {@code config/<case>/input.*} converts to {@code expected.yaml}, report included.
 * {@code -Pcosid.migration.updateGoldens=true} rewrites the goldens in the source tree.
 */
class ConfigGoldenTest {
    @ParameterizedTest
    @ValueSource(strings = {"yaml/input.yaml", "properties/input.properties", "segments-only/input.yaml", "edge/input.yaml"})
    void convertsToTheGolden(String input) throws IOException {
        Path dir = Files.createTempDirectory("cosid-config");
        Path output = dir.resolve("application-5.yaml");
        Path inputFile = MigrationScenarioTest.resource("config/" + input);
        // Each case has the apply-result.json of its keys; the edge case runs without one, its segment generators named
        // as new, and its static slot without not-before is reported.
        boolean edge = input.startsWith("edge/");
        Path result = edge ? null : inputFile.resolveSibling("apply-result.json");
        new Migration(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
            .config(new Migration.ConfigOptions(inputFile, result, output, ZoneId.of("Asia/Shanghai"), edge ? Set.of("friendly", "grouped", "grouped-inside", "grouped-plain") : Set.of()));
        String actual = Files.readString(output);
        Path golden = inputFile.resolveSibling("expected.yaml");
        if (Boolean.getBoolean("cosid.migration.updateGoldens")) {
            Path source = Paths.get(System.getProperty("user.dir"), "src/test/resources/config", input).resolveSibling("expected.yaml");
            Files.writeString(source, actual);
            return;
        }
        assertThat(actual, is(Files.readString(golden)));
    }
}
