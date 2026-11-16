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

package me.ahoo.cosid.migration.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import me.ahoo.cosid.migration.ApplyResult;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ahoo-Wang/CosId#1446: a dotted 4.x generator name such as {@code orders.Order} must survive migration config
 * parsing in full, in YAML (bracketed key) and in {@code .properties} (bracketed key, the Spring Boot Map binding
 * contract), so its step and namespace read under the original name and its migrated key matches. An unbracketed
 * dotted YAML key binds to no map entry in 4.x, so it is reported, not converted; and the converted YAML writes a
 * dotted name bracketed, or a 5.0 application binds no generator from it.
 */
class FlatConfigDottedNamesTest {
    private static final String YAML = """
        cosid:
          namespace: shop
          snowflake: { enabled: false }
          segment:
            enabled: true
            share: { enabled: false }
            distributor: { type: jdbc }
            provider:
              "[orders.Order]": { step: 50, namespace: custom }
        """;

    @Test
    void yamlBracketedNameIsRecognisedInFull() {
        FlatConfig config = FlatConfig.ofYaml(YAML);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("orders.Order")));
        Legacy4x legacy = new Legacy4x(config);
        assertThat(legacy.segments().keySet(), is(Set.of("orders.Order")));
        String prefix = legacy.segments().get("orders.Order");
        assertThat(config.number(prefix + ".step", 10), is(50L));
        assertThat(config.get(prefix + ".namespace"), is("custom"));
        assertThat(legacy.extraNamespaces().get("custom"), is(Set.of("orders.Order")));
    }

    @Test
    void yamlDottedKeyWithoutBracketsIsReportedNotConverted() {
        // #1446 review: an unbracketed dotted YAML key binds to no map entry in 4.x, so it names no generator
        // and converts to none; it is reported instead. A bracketed key ("[orders.Order]") is the dotted name.
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              segment:
                enabled: true
                share: { enabled: false }
                provider:
                  orders.Order: { step: 50, namespace: custom }
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of()));
        assertThat(config.ignoredDottedKeys(), is(Set.of("cosid.segment.provider.orders.Order")));
        assertThat(new Legacy4x(config).segments().keySet(), is(Set.of()));
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(config, null);
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        assertThat(generators.keySet(), is(Set.of()));
        // The hint must name the whole dotted entry: "[Order]" would create a generator named `Order`.
        assertThat(conversion.report(),
            hasItem("cosid.segment.provider.orders.Order: a dotted key without brackets is no map entry name in Spring Boot binding;"
                + " ignored (write it as \"[orders.Order]\" to name an entry)"));
    }

    @Test
    void yamlMultiDotKeyWithoutBracketsIsReportedNotConverted() {
        // Debate round 2: the multi-dot variant of the unbracketed key must pin the full hint "[a.b.c]";
        // after the last dot it would be "[c]", which names no generator either.
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              segment:
                enabled: true
                share: { enabled: false }
                provider:
                  a.b.c: { step: 50 }
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of()));
        assertThat(config.ignoredDottedKeys(), is(Set.of("cosid.segment.provider.a.b.c")));
        assertThat(new Legacy4x(config).segments().keySet(), is(Set.of()));
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(config, null);
        assertThat(conversion.report(),
            hasItem("cosid.segment.provider.a.b.c: a dotted key without brackets is no map entry name in Spring Boot binding;"
                + " ignored (write it as \"[a.b.c]\" to name an entry)"));
    }

    @Test
    void yamlDottedKeyWithoutBracketsInSnowflakeProviderIsReported() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              snowflake:
                enabled: true
                provider:
                  trace.Span: { epoch: 1600000000000, namespace: custom }
            """);
        assertThat(config.names("cosid.snowflake.provider"), is(Set.of()));
        assertThat(config.ignoredDottedKeys(), is(Set.of("cosid.snowflake.provider.trace.Span")));
        // Only the shared default snowflake exists; the ignored key creates none.
        assertThat(new Legacy4x(config).snowflakes().keySet(), is(Set.of("default")));
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(config, null);
        assertThat(conversion.report(),
            hasItem(containsString("(write it as \"[trace.Span]\" to name an entry)")));
    }

    @Test
    void yamlDottedKeyWithoutBracketsWithEmptyObjectIsReported() {
        // The empty-object path must leave no trace: pre-fix it declared the entry with every default.
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              segment:
                enabled: true
                share: { enabled: false }
                provider:
                  orders.Order: {}
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of()));
        assertThat(config.ignoredDottedKeys(), is(Set.of("cosid.segment.provider.orders.Order")));
        assertThat(new Legacy4x(config).segments().keySet(), is(Set.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertedYamlBracketsTheDottedGeneratorName() {
        // #1446 review: the converted YAML must bracket a dotted generator name, or a 5.0 application binds no
        // generator from it. The model keeps the raw name, so its unmigratedSegments comparison still works.
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(FlatConfig.ofYaml(YAML), null);
        String yaml = conversion.yaml();
        assertThat(yaml, containsString("[orders.Order]"));
        assertThat(yaml, not(containsString("\n    orders.Order:")));
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        assertThat(generators.keySet(), is(Set.of("orders.Order")));
        // The bracketed YAML re-reads as the same name with the same step.
        FlatConfig reread = FlatConfig.ofYaml(yaml);
        assertThat(reread.names("cosid.generators"), is(Set.of("orders.Order")));
        assertThat(reread.number("cosid.generators.[orders.Order].step", 10), is(50L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertedYamlBracketsDottedNamesInEveryEntryMap() {
        // Machines and stores cannot carry a dot from a conversion today (machine names are sanitised, store
        // names are types), but the same bracketing applies to their entry maps.
        Map<String, Object> cosid = new LinkedHashMap<>();
        cosid.put("namespace", "shop");
        cosid.put("generators", Map.of("orders.Order", Map.of("strategy", "segment")));
        cosid.put("machines", Map.of("a.b", Map.of("static", Map.of("slot", 1))));
        cosid.put("stores", Map.of("s.t", Map.of("type", "jdbc")));
        ConfigConverter.Conversion conversion = new ConfigConverter.Conversion(Map.of("cosid", cosid), List.of());
        String yaml = conversion.yaml();
        assertThat(yaml, containsString("[orders.Order]"));
        assertThat(yaml, containsString("[a.b]"));
        assertThat(yaml, containsString("[s.t]"));
        Map<String, Object> modelCosid = (Map<String, Object>) conversion.model().get("cosid");
        assertThat(((Map<String, Object>) modelCosid.get("generators")).keySet(), is(Set.of("orders.Order")));
        assertThat(((Map<String, Object>) modelCosid.get("machines")).keySet(), is(Set.of("a.b")));
        assertThat(((Map<String, Object>) modelCosid.get("stores")).keySet(), is(Set.of("s.t")));
    }

    @Test
    void propertiesBracketedNameIsRecognisedInFull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.writeString(file, """
            cosid.namespace=shop
            cosid.segment.enabled=true
            cosid.segment.share.enabled=false
            cosid.segment.provider[orders.Order].step=50
            cosid.segment.provider[orders.Order].namespace=custom
            """);
        FlatConfig config = FlatConfig.load(file);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("orders.Order")));
        Legacy4x legacy = new Legacy4x(config);
        assertThat(legacy.segments().keySet(), is(Set.of("orders.Order")));
        assertThat(config.number(legacy.segments().get("orders.Order") + ".step", 10), is(50L));
        assertThat(config.get(legacy.segments().get("orders.Order") + ".namespace"), is("custom"));
    }

    @Test
    void snowflakeBracketedNameIsRecognisedInFull() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              snowflake:
                enabled: true
                share: { enabled: false }
                provider:
                  "[trace.Span]": { epoch: 1600000000000 }
            """);
        assertThat(config.names("cosid.snowflake.provider"), is(Set.of("trace.Span")));
        assertThat(new Legacy4x(config).snowflakes().keySet(), is(Set.of("trace.Span")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void converterKeepsStepAndMatchesMigratedKey() {
        ApplyResult.SegmentResult segment = new ApplyResult.SegmentResult("shop", "orders.Order", "shop/orders.Order", 100, 101, 101, null);
        ApplyResult result = new ApplyResult(ApplyResult.FORMAT, "plan-sha", "shop", null, null, null, null, List.of(), 0, 0, 0, 0, null, 0,
            false, 0, 0, null, List.of(segment), List.of(), null, null, null);
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(FlatConfig.ofYaml(YAML), result);
        assertThat(conversion.unmigratedSegments(result), is(List.of()));
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        assertThat(generators.keySet(), is(Set.of("orders.Order")));
        assertThat(((Map<String, Object>) generators.get("orders.Order")).get("step"), is(50L));
        // The report may note the migrated offset, but nothing of this generator may be reported dropped.
        assertThat(conversion.report().stream().noneMatch(line -> line.contains("dropped")), is(true));
    }

    @Test
    void plainNamesAndListIndicesAreUnchanged() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              segment:
                enabled: true
                share: { enabled: false }
                provider:
                  order: { step: 50 }
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("order")));
        assertThat(config.number(new Legacy4x(config).segments().get("order") + ".step", 10), is(50L));
        assertThat(FlatConfig.canonical("cosid.segment.provider.biz_order.step"), is("cosid.segment.provider.biz_order.step"));
        assertThat(FlatConfig.canonical("cosid.machine.machine-bit"), is("cosid.machine.machine-bit"));
        assertThat(FlatConfig.canonical("cosid.foo[0].bar"), is("cosid.foo[0].bar"));
    }

    @Test
    void canonicalKeepsBracketsOnlyForDottedNames() {
        assertThat(FlatConfig.canonical("cosid.segment.provider[orders.Order].step"), is("cosid.segment.provider.[orders.Order].step"));
        assertThat(FlatConfig.canonical("cosid.segment.provider[Orders.Order].step"), is("cosid.segment.provider.[Orders.Order].step"));
        assertThat(FlatConfig.canonical("cosid.segment.provider[order].step"), is("cosid.segment.provider.order.step"));
        // Without brackets a .properties key is nested maps per the Spring Boot Map binding contract, not a dotted
        // name: the name is `orders`, and `Order` is kebab-cased like any other attribute part.
        assertThat(FlatConfig.canonical("cosid.segment.provider.orders.Order.step"), is("cosid.segment.provider.orders.order.step"));
    }

    @Test
    void canonicalKeepsMalformedBracketsAsWritten() {
        // An unclosed bracket or empty brackets are malformed input: keep the text instead of dropping a character
        // or producing a double dot.
        assertThat(FlatConfig.canonical("cosid.segment.provider[order"), is("cosid.segment.provider[order"));
        assertThat(FlatConfig.canonical("cosid.foo[].bar"), is("cosid.foo[].bar"));
        assertThat(FlatConfig.canonical("cosid.segment.provider[].step"), is("cosid.segment.provider[].step"));
    }

    @Test
    void capitalisedYamlKeysStillBracketTheDottedName() {
        // Spring's relaxed binding accepts `Segment:`/`Provider:`; the flatten wrap must too.
        FlatConfig config = FlatConfig.ofYaml("""
            Cosid:
              Segment:
                enabled: true
                share: { enabled: false }
                Provider:
                  "[orders.Order]": { step: 50 }
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("orders.Order")));
        assertThat(config.number(new Legacy4x(config).segments().get("orders.Order") + ".step", 10), is(50L));
    }

    @Test
    void emptyObjectDottedEntryIsRecognised() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              segment:
                enabled: true
                share: { enabled: false }
                provider:
                  "[orders.Order]": {}
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("orders.Order")));
        assertThat(new Legacy4x(config).segments().keySet(), is(Set.of("orders.Order")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uppercaseNameEndToEnd() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              snowflake: { enabled: false }
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider:
                  "[Orders.Order]": { step: 7, namespace: custom }
            """);
        Legacy4x legacy = new Legacy4x(config);
        assertThat(legacy.segments().keySet(), is(Set.of("Orders.Order")));
        assertThat(config.number(legacy.segments().get("Orders.Order") + ".step", 10), is(7L));
        assertThat(legacy.extraNamespaces().get("custom"), is(Set.of("Orders.Order")));
        ApplyResult.SegmentResult segment = new ApplyResult.SegmentResult("shop", "Orders.Order", "shop/Orders.Order", 100, 101, 101, null);
        ApplyResult result = new ApplyResult(ApplyResult.FORMAT, "plan-sha", "shop", null, null, null, null, List.of(), 0, 0, 0, 0, null, 0,
            false, 0, 0, null, List.of(segment), List.of(), null, null, null);
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(config, result);
        assertThat(conversion.unmigratedSegments(result), is(List.of()));
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        assertThat(generators.keySet(), is(Set.of("Orders.Order")));
        assertThat(((Map<String, Object>) generators.get("Orders.Order")).get("step"), is(7L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupedDottedSegmentKeepsPatternAndStep() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              snowflake: { enabled: false }
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider:
                  "[orders.Order]": { step: 50, namespace: custom, group: { by: year_month, pattern: yyyyMM } }
            """);
        Legacy4x legacy = new Legacy4x(config);
        assertThat(legacy.groupPatterns(), is(Map.of("orders.Order", "yyyyMM")));
        ApplyResult.SegmentResult segment = new ApplyResult.SegmentResult("shop", "orders.Order", "shop/orders.Order/202611", 100, 101, 101, null);
        ApplyResult result = new ApplyResult(ApplyResult.FORMAT, "plan-sha", "shop", null, null, null, null, List.of(), 0, 0, 0, 0, null, 0,
            false, 0, 0, null, List.of(segment), List.of(), null, null, null);
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(config, result);
        assertThat(conversion.unmigratedSegments(result), is(List.of()));
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        Map<String, Object> generator = (Map<String, Object>) generators.get("orders.Order");
        assertThat(generator.get("strategy"), is("periodic-segment"));
        assertThat(generator.get("step"), is(50L));
        assertThat(conversion.report().stream().noneMatch(line -> line.contains("dropped")), is(true));
    }

    @Test
    void snowflakeWithOwnNamespaceGetsNamedMachine() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              snowflake:
                enabled: true
                provider:
                  "[trace.Span]": { epoch: 1600000000000, namespace: custom }
            """);
        Legacy4x legacy = new Legacy4x(config);
        assertThat(legacy.machineNamespaces().get("custom").machine(), is("custom"));
        assertThat(legacy.machineNamespaces().get("custom").owners(), is(List.of("cosid.snowflake.provider.[trace.Span]")));
        assertThat(legacy.extraNamespaces().get("custom"), is(Set.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiDotNameEndToEnd() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              snowflake: { enabled: false }
              segment:
                enabled: true
                share: { enabled: false }
                distributor: { type: jdbc }
                provider:
                  "[a.b.c]": { step: 50 }
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("a.b.c")));
        ApplyResult.SegmentResult segment = new ApplyResult.SegmentResult("shop", "a.b.c", "shop/a.b.c", 100, 101, 101, null);
        ApplyResult result = new ApplyResult(ApplyResult.FORMAT, "plan-sha", "shop", null, null, null, null, List.of(), 0, 0, 0, 0, null, 0,
            false, 0, 0, null, List.of(segment), List.of(), null, null, null);
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(config, result);
        assertThat(conversion.unmigratedSegments(result), is(List.of()));
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        assertThat(generators.keySet(), is(Set.of("a.b.c")));
        assertThat(conversion.report().stream().noneMatch(line -> line.contains("dropped")), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertWithoutApplyResultKeepsStep() {
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneId.of("Asia/Shanghai")).convert(FlatConfig.ofYaml(YAML), null);
        Map<String, Object> generators = (Map<String, Object>) ((Map<String, Object>) conversion.model().get("cosid")).get("generators");
        assertThat(((Map<String, Object>) generators.get("orders.Order")).get("step"), is(50L));
        assertThat(conversion.report().stream().noneMatch(line -> line.contains("dropped")), is(true));
    }

    @Test
    void layoutsOf5KeepsDottedGeneratorName() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              generators:
                "[orders.Order]": { strategy: cosid80 }
            """);
        assertThat(ConfigConverter.layoutsOf5(config).keySet(), is(Set.of("orders.Order")));
        assertThat(ConfigConverter.layoutsOf5(config).get("orders.Order"), is(Layout.COSID_80));
    }
}
