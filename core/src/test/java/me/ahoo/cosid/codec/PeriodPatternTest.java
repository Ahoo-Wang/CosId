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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.segment.Period;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.function.UnaryOperator;

/**
 * {@link GroupedCodec#datePrefix(Period, String, String, IdCodec, UnaryOperator)}: the period key written in a pattern
 * of its own (D51, #1501), while the keys keep the preset's pattern.
 */
class PeriodPatternTest {
    private static final IdCodec DECIMAL = new IdCodec() {
        @Override
        public String encode(long id) {
            return Long.toString(id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text);
        }
    };
    private static final UnaryOperator<IdCodec> FIRST = codec -> codec;

    @Test
    void theFourXDocumentNumbers() {
        // 4.x: group { by: year_month_day, pattern: yyMMdd }, converter { prefix: TO-, group-prefix: { enabled: true } }.
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyMMdd", "-", DECIMAL, outer -> outer.withPrefix("TO-"));
        assertThat(codec.encode(new GroupedId("20261124", 1)), is("TO-261124-1"));
        assertThat(codec.encode(new GroupedId("20261124", 2)), is("TO-261124-2"));
        assertThat(codec.encode(new GroupedId("20261125", 1)), is("TO-261125-1"));
        // Decoding gives the key back in the preset's pattern, the one the store keys use.
        assertThat(codec.decode("TO-261124-1"), is(new GroupedId("20261124", 1)));
        assertThat(codec.decode("TO-261125-1"), is(new GroupedId("20261125", 1)));
    }

    @Test
    void thePatternChangesTheTextNotTheKey() {
        GroupedCodec preset = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "-", DECIMAL, FIRST);
        GroupedCodec patterned = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyMMdd", "-", DECIMAL, FIRST);
        GroupedId id = new GroupedId("20261124", 42);
        assertThat(preset.encode(id), is("20261124-42"));
        assertThat(patterned.encode(id), is("261124-42"));
        assertThat(patterned.decode(patterned.encode(id)), is(preset.decode(preset.encode(id))));
    }

    @Test
    void everyPresetTakesAPattern() {
        assertThat(GroupedCodec.datePrefix(Period.YEAR, "yy", "-", DECIMAL, FIRST).encode(new GroupedId("2026", 7)), is("26-7"));
        assertThat(GroupedCodec.datePrefix(Period.YEAR, "yy", "-", DECIMAL, FIRST).decode("26-7"), is(new GroupedId("2026", 7)));
        assertThat(GroupedCodec.datePrefix(Period.YEAR_MONTH, "yyMM", "-", DECIMAL, FIRST).encode(new GroupedId("202611", 7)), is("2611-7"));
        assertThat(GroupedCodec.datePrefix(Period.YEAR_MONTH, "yyMM", "-", DECIMAL, FIRST).decode("2611-7"), is(new GroupedId("202611", 7)));
    }

    @Test
    void aFixedLengthDecodesWithoutDelimiterAndMayHoldIt() {
        GroupedCodec packed = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyMMdd", "", Radix.of(10, 6, true), FIRST);
        assertThat(packed.encode(new GroupedId("20261124", 42)), is("261124000042"));
        assertThat(packed.decode("261124000042"), is(new GroupedId("20261124", 42)));

        GroupedCodec dashed = GroupedCodec.datePrefix(Period.YEAR_MONTH, "yyyy-MM", "-", DECIMAL, outer -> outer.withPrefix("P"));
        assertThat(dashed.encode(new GroupedId("202611", 42)), is("P2026-11-42"));
        assertThat(dashed.decode("P2026-11-42"), is(new GroupedId("202611", 42)));
        assertThrows(IllegalArgumentException.class, () -> dashed.decode("P2026-1-42"), "too short for the pattern");
    }

    @Test
    void aVaryingLengthEndsWhereItsPatternDoes() {
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyyy-M-d", "-", DECIMAL, FIRST);
        assertThat(codec.encode(new GroupedId("20260109", 42)), is("2026-1-9-42"));
        assertThat(codec.decode("2026-1-9-42"), is(new GroupedId("20260109", 42)));
        assertThat(codec.decode("2026-11-24-42"), is(new GroupedId("20261124", 42)));
        IllegalArgumentException spelling = assertThrows(IllegalArgumentException.class, () -> codec.decode("2026-01-09-42"));
        assertThat(spelling.getMessage(), containsString("is not a YEAR_MONTH_DAY period of the pattern [yyyy-M-d]"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("2026-1-9+42"), "no delimiter after the key");
        assertThrows(IllegalArgumentException.class, () -> codec.decode("x-42"), "no key");
        UnsupportedOperationException packed = assertThrows(UnsupportedOperationException.class,
            () -> GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyyy-M-d", "", DECIMAL, FIRST).decode("2026-1-942"));
        assertThat(packed.getMessage(), containsString("no known length"));
    }

    @Test
    void textFieldsAndQuotedLettersAreRead() {
        // Locale.ROOT: the same text on every machine, whatever its default locale.
        GroupedCodec month = GroupedCodec.datePrefix(Period.YEAR_MONTH, "MMMyyyy", "-", DECIMAL, FIRST);
        assertThat(month.encode(new GroupedId("202611", 3)), is("Nov2026-3"));
        assertThat(month.decode("Nov2026-3"), is(new GroupedId("202611", 3)));
        // A quoted y is a literal, not a year.
        GroupedCodec quoted = GroupedCodec.datePrefix(Period.YEAR, "'y'yy", "", DECIMAL, FIRST);
        assertThat(quoted.encode(new GroupedId("2026", 3)), is("y263"));
        assertThat(quoted.decode("y263"), is(new GroupedId("2026", 3)));
    }

    @Test
    void decodingIsStrict() {
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyMMdd", "-", DECIMAL, outer -> outer.withPrefix("TO-"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("TO-261332-1"), "no 13th month");
        assertThrows(IllegalArgumentException.class, () -> codec.decode("TO-260229-1"), "2026 is no leap year");
        assertThrows(IllegalArgumentException.class, () -> codec.decode("TO-2611-1"), "too short");
        assertThrows(IllegalArgumentException.class, () -> codec.decode("261124-1"), "no business prefix");
    }

    @Test
    void encodingRefusesAKeyOfAnotherPeriod() {
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyMMdd", "-", DECIMAL, FIRST);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> codec.encode(new GroupedId("202611", 1)));
        assertThat(e.getMessage(), containsString("is not a YEAR_MONTH_DAY period key"));
    }

    @Test
    void aTwoDigitYearRefusesAPeriodItWouldReadBackAsAnother() {
        // yy reads 2000 to 2099: 1999 would decode as 2099, so it is refused rather than issued.
        GroupedCodec codec = GroupedCodec.datePrefix(Period.YEAR_MONTH_DAY, "yyMMdd", "-", DECIMAL, FIRST);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> codec.encode(new GroupedId("19991231", 1)));
        assertThat(e.getMessage(), containsString("cannot write the YEAR_MONTH_DAY period [19991231]"));
        assertThat(codec.encode(new GroupedId("20991231", 1)), is("991231-1"));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new GroupedId("21000101", 1)));
    }

    @Test
    void thePatternMustTellExactlyThePeriod() {
        assertRefused(Period.YEAR_MONTH_DAY, "yyMM", "does not tell a whole YEAR_MONTH_DAY period");
        assertRefused(Period.YEAR_MONTH_DAY, "MMdd", "does not tell a whole YEAR_MONTH_DAY period");
        assertRefused(Period.YEAR_MONTH, "yyMMdd", "has a field finer than a YEAR_MONTH period");
        assertRefused(Period.YEAR_MONTH_DAY, "yyMMddHH", "has a field finer than a YEAR_MONTH_DAY period");
        assertRefused(Period.YEAR, "'year'", "does not tell a whole YEAR period");
        assertRefused(Period.YEAR_MONTH_DAY, "yyMMdd{", "is not a date pattern");
        assertRefused(Period.YEAR_MONTH_DAY, "", "pattern must not be empty");
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix(Period.YEAR, null, "-", DECIMAL, FIRST));
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix(null, "yy", "-", DECIMAL, FIRST));
    }

    @Test
    void aCustomPeriodHasNoKeysToRead() {
        Period quarter = new Period() {
            @Override
            public String keyOf(Instant instant, ZoneId zone) {
                return "2026Q4";
            }

            @Override
            public Instant endOf(String key, ZoneId zone) {
                return Instant.EPOCH;
            }
        };
        assertRefused(quarter, "yyyy", "needs a preset period");
    }

    private static void assertRefused(Period period, String pattern, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GroupedCodec.datePrefix(period, pattern, "-", DECIMAL, FIRST));
        assertThat(e.getMessage(), containsString(message));
    }
}
