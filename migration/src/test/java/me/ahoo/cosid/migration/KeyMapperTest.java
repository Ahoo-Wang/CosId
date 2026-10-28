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
import static org.hamcrest.Matchers.nullValue;

import me.ahoo.cosid.store.StoreKeys;

import org.junit.jupiter.api.Test;

import java.util.Map;

class KeyMapperTest {
    private final KeyMapper mapper = new KeyMapper("app", Map.of("legacy", "renamed"), Map.of("dashed", "yyyy-MM-dd", "short", "yyMM", "year2", "yy"));

    @Test
    void plainSegmentsKeepTheirName() {
        KeyMapper.Mapping mapping = mapper.map("order");
        assertThat(mapping.mapped(), is(true));
        assertThat(mapping.targetKey(), is("app/order"));
        assertThat(mapping.period(), nullValue());
        assertThat(mapper.map("a.b").targetKey(), is("app/a.b"));
    }

    @Test
    void theShareIsTheDefaultGeneratorAndRenamesApply() {
        assertThat(mapper.map("__share__").targetKey(), is("app/default"));
        assertThat(mapper.map("legacy").targetKey(), is("app/renamed"));
        assertThat(new KeyMapper("app", Map.of("__share__", "segment-share"), Map.of()).map("__share__").targetKey(), is("app/segment-share"));
    }

    @Test
    void groupKeysOfThePresetsMapToPeriodKeys() {
        assertThat(mapper.map("bill@2026").targetKey(), is("app/bill/2026"));
        assertThat(mapper.map("bill@2026").period(), is("YEAR"));
        assertThat(mapper.map("bill@202610").period(), is("YEAR_MONTH"));
        assertThat(mapper.map("bill@20261026").targetKey(), is("app/bill/20261026"));
        assertThat(mapper.map("bill@20261026").period(), is("YEAR_MONTH_DAY"));
    }

    @Test
    void otherGroupKeysNeedTheirPattern() {
        assertThat(mapper.map("bill@20261332").mapped(), is(false));
        assertThat(mapper.map("bill@26-10").problem(), containsString("--group-pattern bill="));
        assertThat(mapper.map("dashed@2026-10-26").targetKey(), is("app/dashed/20261026"));
        assertThat(mapper.map("short@2610").targetKey(), is("app/short/202610"));
        assertThat(mapper.map("year2@26").targetKey(), is("app/year2/2026"));
        assertThat(mapper.map("dashed@20261026").mapped(), is(false));
    }

    @Test
    void namesWith5ForbiddenCharactersAreNotMigrated() {
        assertThat(mapper.map("a/b").mapped(), is(false));
        assertThat(mapper.map("@2026").mapped(), is(false));
        // R1-spi-02: names and keys that some 5.0 backend cannot store.
        assertThat(mapper.map("order item").problem(), containsString("the name must not contain whitespace; use --rename"));
        assertThat(mapper.map("..").mapped(), is(false));
        String longName = "n".repeat(StoreKeys.MAX_KEY_LENGTH);
        assertThat(mapper.map(longName).problem(), containsString("is longer than 255 characters"));
        assertThat(mapper.map("n".repeat(StoreKeys.MAX_KEY_LENGTH - "app/".length())).mapped(), is(true));
    }

    @Test
    void planRefusesANamespace5Cannot() {
        Migration.PlanOptions options = new Migration.PlanOptions(StoreLocation.parse("jdbc:h2:mem:x", Map.of()), StoreLocation.parse("jdbc:h2:mem:y", Map.of()),
            null, null, "{cosid}", null, null, null, java.time.Duration.ofSeconds(1), java.time.Duration.ZERO, Map.of(), Map.of(),
            java.nio.file.Path.of("plan.json"));
        MigrationAbortedException refused = org.junit.jupiter.api.Assertions.assertThrows(MigrationAbortedException.class,
            () -> new Migration(System.out).plan(options));
        assertThat(refused.getMessage(), containsString("--namespace: namespace [{cosid}] must be 1 to 100"));
    }
}
