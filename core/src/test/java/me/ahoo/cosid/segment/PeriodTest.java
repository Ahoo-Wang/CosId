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

package me.ahoo.cosid.segment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class PeriodTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void keysFollowTheZone() {
        Instant instant = Instant.parse("2026-12-31T17:00:00Z");
        assertThat(Period.YEAR.keyOf(instant, ZoneOffset.UTC), is("2026"));
        assertThat(Period.YEAR.keyOf(instant, SHANGHAI), is("2027"));
        assertThat(Period.YEAR_MONTH.keyOf(instant, ZoneOffset.UTC), is("202612"));
        assertThat(Period.YEAR_MONTH.keyOf(instant, SHANGHAI), is("202701"));
        assertThat(Period.YEAR_MONTH_DAY.keyOf(instant, ZoneOffset.UTC), is("20261231"));
        assertThat(Period.YEAR_MONTH_DAY.keyOf(instant, SHANGHAI), is("20270101"));
    }

    @Test
    void endIsTheStartOfTheNextPeriodInTheZone() {
        assertThat(Period.YEAR.endOf("2026", ZoneOffset.UTC), is(Instant.parse("2027-01-01T00:00:00Z")));
        assertThat(Period.YEAR_MONTH.endOf("202612", ZoneOffset.UTC), is(Instant.parse("2027-01-01T00:00:00Z")));
        assertThat(Period.YEAR_MONTH.endOf("202602", ZoneOffset.UTC), is(Instant.parse("2026-03-01T00:00:00Z")));
        assertThat(Period.YEAR_MONTH_DAY.endOf("20261026", SHANGHAI), is(Instant.parse("2026-10-26T16:00:00Z")));
        assertThat(Period.YEAR_MONTH_DAY.endOf("20240228", ZoneOffset.UTC), is(Instant.parse("2024-02-29T00:00:00Z")));
    }

    @Test
    void keyAndEndAgree() {
        for (Period period : new Period[] {Period.YEAR, Period.YEAR_MONTH, Period.YEAR_MONTH_DAY}) {
            Instant instant = Instant.parse("2026-10-26T10:15:30Z");
            String key = period.keyOf(instant, SHANGHAI);
            Instant end = period.endOf(key, SHANGHAI);
            assertThat(end.isAfter(instant), is(true));
            assertThat(period.keyOf(end.minusMillis(1), SHANGHAI), is(key));
            assertThat(period.keyOf(end, SHANGHAI).equals(key), is(false));
        }
    }

    @Test
    void invalidKeysAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Period.YEAR_MONTH_DAY.endOf("20261332", ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> Period.YEAR_MONTH.endOf("2026", ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> Period.YEAR.endOf("x", ZoneOffset.UTC));
    }
}
