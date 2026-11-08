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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.codec.DatePrefix;
import me.ahoo.cosid.internal.RuntimePlan;
import me.ahoo.cosid.internal.StoreProviders;
import me.ahoo.cosid.migration.config.ConfigConverter;
import me.ahoo.cosid.migration.config.FlatConfig;
import me.ahoo.cosid.migration.config.Legacy4x;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GeneratorSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.RuntimeOptions;
import me.ahoo.cosid.runtime.RuntimeSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code config} keeps the meaning of a 4.x configuration, or writes nothing: each case converts, binds as a 5.0
 * application binds it, and the runtime's own checks ({@link RuntimePlan}) accept it.
 */
class ConfigFidelityTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    /** The zone a bound spec of {@link ConfigBindingTest#specOf} has. */
    private static final ZoneId SPEC_ZONE = ZoneId.of("UTC");

    @TempDir
    Path dir;

    private ConfigConverter.Conversion convert(String yaml) {
        return new ConfigConverter(UTC).convert(FlatConfig.ofYaml(yaml), null);
    }

    /** The conversion of a YAML file as a 5.0 application binds it, checked as the runtime checks it at the start. */
    private RuntimeSpec spec(ConfigConverter.Conversion conversion) throws IOException {
        Path output = dir.resolve("converted-" + System.nanoTime() + ".yaml");
        conversion.write(output);
        RuntimeSpec spec = ConfigBindingTest.specOf(output);
        RuntimePlan.of(spec, RuntimeOptions.defaults(), new StoreProviders(List.of()));
        return spec;
    }

    private static List<CodecSpec> codecOf(RuntimeSpec spec, String generator) {
        return ((GeneratorSpec.Segment) spec.generators().get(generator)).codec();
    }

    // #1456: the blanks of a prefix, a suffix or a delimiter are part of every id.
    @Test
    void textsKeepTheirBlanks() throws IOException {
        String yaml = """
            cosid:
              segment:
                enabled: true
                share:
                  converter:
                    type: to_string
                    prefix: "Order "
                    suffix: " ready"
                    date-prefix:
                      enabled: true
                      delimiter: " "
            """;
        List<CodecSpec> codec = codecOf(spec(convert(yaml)), "default");
        assertThat(codec, is(List.of(new CodecSpec.DatePrefix("yyMMdd", " ", DatePrefix.Source.NOW, SPEC_ZONE), new CodecSpec.Prefix("Order "),
            new CodecSpec.Suffix(" ready"))));
        Path properties = dir.resolve("blanks.properties");
        Files.writeString(properties, """
            cosid.segment.enabled = true
            cosid.segment.share.converter.type = to_string
            cosid.segment.share.converter.prefix = Order\\u0020
            cosid.segment.share.converter.suffix = \\u0020ready
            cosid.segment.share.step = 20\\u0020
            """);
        FlatConfig config = FlatConfig.load(properties);
        assertThat(config.text("cosid.segment.share.converter.prefix"), is("Order "));
        assertThat(config.text("cosid.segment.share.converter.suffix"), is(" ready"));
        assertThat(config.number("cosid.segment.share.step", 10), is(20L));
    }

    // #1468: Spring reads on, yes and 1 as true; a value that is no boolean is an error, never false.
    @Test
    void booleansReadAsSpringReadsThem() {
        for (String yes : List.of("true", "on", "yes", "1", "TRUE", "On", " yes ")) {
            ConfigConverter.Conversion conversion = convert("cosid:\n  segment:\n    enabled: \"" + yes + "\"\n");
            assertThat(yes, generators(conversion).keySet(), is(Set.of("default")));
        }
        for (String no : List.of("false", "off", "no", "0", "OFF")) {
            assertThat(no, generators(convert("cosid:\n  segment:\n    enabled: \"" + no + "\"\n")).keySet(), empty());
        }
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class, () -> convert("cosid:\n  segment:\n    enabled: maybe\n"));
        assertThat(invalid.getMessage(), containsString("cosid.segment.enabled = maybe: not a boolean"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> generators(ConfigConverter.Conversion conversion) {
        return (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
    }

    // #1478: the codec list follows the 4.x chain, inside out, for every before-prefix of the group and date prefixes.
    @Test
    void theCodecListFollowsThe4xChain() throws IOException {
        CodecSpec radix = new CodecSpec.Radix(62, 11, true);
        CodecSpec date = new CodecSpec.DatePrefix("'D'", ":", DatePrefix.Source.NOW, SPEC_ZONE);
        CodecSpec prefix = new CodecSpec.Prefix("P");
        CodecSpec suffix = new CodecSpec.Suffix("S");
        for (boolean groupFirst : List.of(true, false)) {
            for (boolean dateFirst : List.of(true, false)) {
                for (boolean texts : List.of(true, false)) {
                    String yaml = """
                        cosid:
                          segment:
                            enabled: true
                            share:
                              group: { by: YEAR_MONTH_DAY }
                              converter:
                                %s
                                group-prefix: { enabled: true, before-prefix: %s, delimiter: '|' }
                                date-prefix: { enabled: true, before-prefix: %s, pattern: "'D'", delimiter: ':' }
                        """.formatted(texts ? "prefix: P\n        suffix: S" : "", groupFirst, dateFirst);
                    // The 4.x chain: radix, group (first), date (first), prefix, group (not first), date (not first), suffix.
                    List<Object> chain = new ArrayList<>();
                    chain.add(radix);
                    if (groupFirst) {
                        chain.add("period");
                    }
                    if (dateFirst) {
                        chain.add(date);
                    }
                    if (texts) {
                        chain.add(prefix);
                    }
                    if (!groupFirst) {
                        chain.add("period");
                    }
                    if (!dateFirst) {
                        chain.add(date);
                    }
                    if (texts) {
                        chain.add(suffix);
                    }
                    int at = chain.indexOf("period");
                    List<CodecSpec> value = chain.subList(0, at).stream().map(CodecSpec.class::cast).toList();
                    List<CodecSpec> outer = chain.subList(at + 1, chain.size()).stream().map(CodecSpec.class::cast).toList();
                    RuntimeSpec spec = spec(convert(yaml));
                    GroupedCodecSpec codec = ((GeneratorSpec.PeriodicSegment) spec.generators().get("default")).codec();
                    assertThat("group first " + groupFirst + ", date first " + dateFirst + ", texts " + texts, codec,
                        is(new GroupedCodecSpec.DatePrefix("|", value, outer)));
                }
            }
        }
        // Without a group: the date prefix outside the prefix when its before-prefix is false.
        String plain = """
            cosid:
              segment:
                enabled: true
                share:
                  converter:
                    prefix: P
                    date-prefix: { enabled: true, before-prefix: false, pattern: "'D'", delimiter: ':' }
            """;
        ConfigConverter.Conversion conversion = convert(plain);
        assertThat(codecOf(spec(conversion), "default"), is(List.of(radix, prefix, date)));
        assertThat(conversion.report(), not(hasItem(containsString("check the order"))));
    }

    // #1463: 4.x pads to at least char-size digits and writes a longer id in full; a 5.0 width is the most digits.
    @Test
    void zeroPaddedDecimalTextConvertsOnlyWhenEveryIdFits() throws IOException {
        String yaml = """
            cosid:
              segment:
                enabled: true
                share:
                  converter:
                    type: to_string
                    to-string: { pad-start: true, char-size: %d }
            """;
        assertThat(codecOf(spec(convert(yaml.formatted(19))), "default"), is(List.of(new CodecSpec.Radix(10, 19, true))));
        ConfigConverter.Conversion twenty = convert(yaml.formatted(20));
        assertThat(twenty.problems(), empty());
        assertThat(codecOf(spec(twenty), "default"), is(List.of(new CodecSpec.Radix(10, 19, true), new CodecSpec.Prefix("0"))));
        for (int charSize : List.of(11, 18)) {
            ConfigConverter.Conversion short4x = convert(yaml.formatted(charSize));
            assertThat(short4x.problems(), hasItem(containsString("pad-start pads to at least char-size " + charSize + " digits")));
        }
        // config writes nothing for a conversion with a problem.
        Path input = dir.resolve("padded.yaml");
        Files.writeString(input, yaml.formatted(11));
        Path output = dir.resolve("padded-5.yaml");
        Migration migration = new Migration(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        MigrationAbortedException aborted = assertThrows(MigrationAbortedException.class,
            () -> migration.config(new Migration.ConfigOptions(input, null, output, UTC, Set.of("default"))));
        assertThat(aborted.getMessage(), containsString("has no equivalent 5.0 one"));
        assertThat(Files.exists(output), is(false));
    }

    // #1462: a store timeout above the default reserve-timeout (3s) raises the reserve-timeout of its generators.
    @Test
    void aLongStoreTimeoutRaisesTheReserveTimeoutOfItsGenerators() throws IOException {
        String yaml = """
            cosid:
              namespace: app
              segment:
                enabled: true
                distributor: { type: jdbc, jdbc: { query-timeout: %s } }
                provider:
                  order: { step: 10 }
                  bill: { group: { by: YEAR } }
            """;
        ConfigConverter.Conversion five = convert(yaml.formatted("5s"));
        assertThat(five.report(), hasItem(containsString("store [jdbc] timeout 5s exceeds the default reserve-timeout 3s")));
        RuntimeSpec spec = spec(five);
        assertThat(spec.stores().get("jdbc").timeout(), is(Duration.ofSeconds(5)));
        assertThat(((GeneratorSpec.Segment) spec.generators().get("default")).batch().reserveTimeout(), is(Duration.ofSeconds(5)));
        assertThat(((GeneratorSpec.Segment) spec.generators().get("order")).batch().reserveTimeout(), is(Duration.ofSeconds(5)));
        assertThat(((GeneratorSpec.PeriodicSegment) spec.generators().get("bill")).batch().reserveTimeout(), is(Duration.ofSeconds(5)));
        for (String within : List.of("2s", "3s", "3000")) {
            ConfigConverter.Conversion conversion = convert(yaml.formatted(within));
            assertThat(within, conversion.report(), not(hasItem(containsString("reserve-timeout"))));
            assertThat(((GeneratorSpec.Segment) spec(conversion).generators().get("order")).batch().reserveTimeout(), is(Duration.ofSeconds(3)));
        }
    }

    // #1472: the 4.x fetch plan AUTO is the 5.0 statement plan.
    @Test
    void theFetchPlanAutoIsTheStatementPlan() throws IOException {
        String yaml = """
            cosid:
              segment:
                enabled: true
                distributor: { type: jdbc, jdbc: { fetch-plan: %s } }
            """;
        for (String auto : List.of("auto", "AUTO")) {
            ConfigConverter.Conversion conversion = convert(yaml.formatted(auto));
            assertThat(spec(conversion).stores().get("jdbc").options(), is(Map.of("fetchPlan", "statement")));
            assertThat(conversion.report(), not(hasItem(containsString("fetch-plan"))));
        }
        assertThat(spec(convert(yaml.formatted("transaction"))).stores().get("jdbc").options(), is(Map.of("fetchPlan", "transaction")));
        assertThat(spec(convert("cosid:\n  segment:\n    enabled: true\n    distributor: { type: jdbc }\n")).stores().get("jdbc").options(), is(Map.of()));
        assertThat(convert(yaml.formatted("lazy")).report(), hasItem(containsString("fetch-plan = lazy: not a 4.x fetch plan")));
    }

    // #1473: a CosIdGenerator whose bits fit a long has no 5.0 cosid80; config refuses it before writing.
    @Test
    void aCosIdGeneratorThatFitsALongIsRefused() throws IOException {
        String yaml = """
            cosid:
              namespace: app
              machine:
                enabled: true
                machine-bit: 10
                distributor: { type: manual, manual: { machine-id: 1 } }
              generator:
                enabled: true
                machine-bit: 10
                sequence-bit: %d
            """;
        for (int seqBits : List.of(8, 9)) {
            assertThat(new Legacy4x(FlatConfig.ofYaml(yaml.formatted(seqBits))).cosidLayoutProblem().orElseThrow(),
                containsString("fit a long: 5.0 cosid80 refuses"));
        }
        assertThat(new Legacy4x(FlatConfig.ofYaml(yaml.formatted(10))).cosidLayoutProblem().isPresent(), is(false));
        assertThat(spec(convert(yaml.formatted(10))).generators().get("cosid"), org.hamcrest.Matchers.instanceOf(GeneratorSpec.CosId80.class));
        Path input = dir.resolve("narrow.yaml");
        Files.writeString(input, yaml.formatted(8));
        Migration migration = new Migration(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertThrows(MigrationAbortedException.class,
            () -> migration.config(new Migration.ConfigOptions(input, null, dir.resolve("narrow-5.yaml"), UTC, Set.of())));
    }
}
