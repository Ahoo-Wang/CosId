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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.config.ConfigConverter;
import me.ahoo.cosid.migration.config.FlatConfig;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

/** {@link FlatConfig} reads values as Spring Boot binds them (#1456, #1468, #1462). */
class FlatConfigTest {
    @Test
    void settingsAreTrimmedAndTextsAreNot() {
        FlatConfig config = FlatConfig.ofYaml("""
            cosid:
              namespace: " app "
              segment:
                share:
                  converter:
                    prefix: " P "
                    suffix: ""
            """);
        assertThat(config.get("cosid.namespace"), is("app"));
        assertThat(config.text("cosid.namespace"), is(" app "));
        assertThat(config.text("cosid.segment.share.converter.prefix"), is(" P "));
        assertThat(config.get("cosid.segment.share.converter.suffix", "none"), is("none"));
        assertThat(config.text("cosid.segment.share.converter.suffix"), is(""));
        assertThat(config.get("cosid.missing"), nullValue());
        assertThat(config.text("cosid.missing"), nullValue());
        assertThat(config.bool("cosid.missing", true), is(true));
    }

    @Test
    void durationsReadAsSpringBootConvertsThem() {
        Map<String, Duration> durations = Map.ofEntries(
            Map.entry("PT5S", Duration.ofSeconds(5)),
            Map.entry("pt1m", Duration.ofMinutes(1)),
            Map.entry("-PT1S", Duration.ofSeconds(-1)),
            Map.entry("7ns", Duration.ofNanos(7)),
            Map.entry("7us", Duration.ofNanos(7_000)),
            Map.entry("7ms", Duration.ofMillis(7)),
            Map.entry("7", Duration.ofMillis(7)),
            Map.entry(" 7s ", Duration.ofSeconds(7)),
            Map.entry("7m", Duration.ofMinutes(7)),
            Map.entry("7h", Duration.ofHours(7)),
            Map.entry("7d", Duration.ofDays(7)),
            Map.entry("7S", Duration.ofSeconds(7)));
        durations.forEach((text, duration) -> assertThat(text, FlatConfig.parseDuration(text), is(duration)));
        for (String invalid : new String[] {"7x", "soon", "PT", "9223372036854775807us", "7.5s"}) {
            assertThrows(IllegalArgumentException.class, () -> FlatConfig.parseDuration(invalid), invalid);
        }
        FlatConfig config = FlatConfig.ofYaml("t:\n  ok: 5s\n  bad: soon\n  empty: \"\"\n");
        assertThat(config.duration("t.ok"), is(Optional.of(Duration.ofSeconds(5))));
        assertThat(config.duration("t.empty"), is(Optional.empty()));
        assertThat(config.duration("t.missing"), is(Optional.empty()));
        assertThat(assertThrows(IllegalArgumentException.class, () -> config.duration("t.bad")).getMessage(), containsString("t.bad = soon"));
    }

    @Test
    void aStoreTimeoutThatIsNoDurationRaisesNothing() {
        ConfigConverter.Conversion conversion = new ConfigConverter(ZoneOffset.UTC).convert(FlatConfig.ofYaml("""
            cosid:
              segment:
                enabled: true
                distributor: { type: jdbc, jdbc: { query-timeout: soon } }
            """), null);
        assertThat(conversion.report(), not(hasItem(containsString("reserve-timeout"))));
    }
}
