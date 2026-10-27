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

package me.ahoo.cosid.codec;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.segment.Period;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

class GroupedCodecTest {
    /** A padded decimal codec, so this test does not depend on Radix (R1-A). */
    private static final IdCodec DECIMAL6 = new IdCodec() {
        @Override
        public String encode(long id) {
            return String.format("%06d", id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text);
        }
    };

    @Test
    void periodDelimiterValue() {
        GroupedCodec codec = GroupedCodec.datePrefix("-", DECIMAL6);
        assertThat(codec.encode(new GroupedId("20261026", 42)), is("20261026-000042"));
        assertThat(codec.decode("20261026-000042"), is(new GroupedId("20261026", 42)));
    }

    @Test
    void splitsAtTheFirstDelimiterSoTheValueCodecMayContainIt() {
        GroupedCodec codec = GroupedCodec.datePrefix("-", DECIMAL6.withPrefix("ORD-"));
        assertThat(codec.encode(new GroupedId("202610", 7)), is("202610-ORD-000007"));
        assertThat(codec.decode("202610-ORD-000007"), is(new GroupedId("202610", 7)));
    }

    @Test
    void composesWithBusinessPrefixes() {
        GroupedCodec codec = GroupedCodec.datePrefix("", DECIMAL6);
        assertThat(codec.encode(new GroupedId("2026", 1)), is("2026000001"));
        assertThrows(UnsupportedOperationException.class, () -> codec.decode("2026000001"));
    }

    @Test
    void refusesTextWithoutThePeriod() {
        GroupedCodec codec = GroupedCodec.datePrefix("-", DECIMAL6);
        assertThrows(IllegalArgumentException.class, () -> codec.decode("000042"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("-000042"));
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix(null, DECIMAL6));
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix("-", null));
    }

    @Test
    void aPresetPeriodDecodesWithoutADelimiter() {
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "", DECIMAL6);
        assertThat(codec.encode(new GroupedId("20261026", 42)), is("20261026000042"));
        assertThat(codec.decode("20261026000042"), is(new GroupedId("20261026", 42)));
        assertThat(GroupedCodec.datePrefix(Period.YEAR, "", DECIMAL6).decode("2026000001"), is(new GroupedId("2026", 1)));
        // Not a key of the period, or too short.
        assertThrows(IllegalArgumentException.class, () -> codec.decode("20261332000042"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("2026102"));
    }

    @Test
    void aPresetPeriodWithADelimiter() {
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH, "-", DECIMAL6.withPrefix("ORD-"));
        assertThat(codec.encode(new GroupedId("202610", 7)), is("202610-ORD-000007"));
        assertThat(codec.decode("202610-ORD-000007"), is(new GroupedId("202610", 7)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("202610_ORD-000007"));
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix(null, "-", DECIMAL6));
    }

    @Test
    void aCustomPeriodSplitsAtTheDelimiter() {
        Period quarter = new Period() {
            @Override
            public String keyOf(Instant instant, ZoneId zone) {
                return "2026Q4";
            }

            @Override
            public Instant endOf(String key, ZoneId zone) {
                if (!key.matches("\\d{4}Q[1-4]")) {
                    throw new IllegalArgumentException(key);
                }
                return Instant.EPOCH;
            }
        };
        GroupedCodec codec = GroupedCodec.datePrefix(quarter, "-", DECIMAL6);
        assertThat(codec.decode("2026Q4-000042"), is(new GroupedId("2026Q4", 42)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("2026Q5-000042"));
        assertThrows(UnsupportedOperationException.class, () -> GroupedCodec.datePrefix(quarter, "", DECIMAL6).decode("2026Q4000042"));
    }
}
