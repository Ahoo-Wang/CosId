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
import static org.hamcrest.Matchers.is;

import me.ahoo.cosid.migration.ApplyResult;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ahoo-Wang/CosId#1446: a dotted 4.x generator name such as {@code orders.Order} must survive migration config
 * parsing in full, in YAML (bracketed or dotted key) and in {@code .properties} (bracketed key, the Spring Boot Map
 * binding contract), so its step and namespace read under the original name and its migrated key matches.
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
    void yamlDottedKeyWithoutBracketsIsRecognisedInFull() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: shop
              segment:
                enabled: true
                share: { enabled: false }
                provider:
                  orders.Order: { step: 50, namespace: custom }
            """);
        assertThat(config.names("cosid.segment.provider"), is(Set.of("orders.Order")));
        Legacy4x legacy = new Legacy4x(config);
        assertThat(legacy.segments().keySet(), is(Set.of("orders.Order")));
        assertThat(config.number(legacy.segments().get("orders.Order") + ".step", 10), is(50L));
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
