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

package me.ahoo.cosid.segment.grouped.date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.segment.grouped.GroupedKey;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The group key and its end around the boundary of a year, a month and a day.
 */
class GroupBoundaryTest {
    
    private static AbstractDateGroupBySupplier<?> supplier(String by, Clock clock) {
        return switch (by) {
            case "year" -> new YearGroupBySupplier(DateTimeFormatter.ofPattern("yyyy"), clock);
            case "month" -> new YearMonthGroupBySupplier(DateTimeFormatter.ofPattern("yyyyMM"), clock);
            default -> new YearMonthDayGroupBySupplier(DateTimeFormatter.ofPattern("yyyyMMdd"), clock);
        };
    }
    
    @ParameterizedTest
    @CsvSource({
        "year, 2025-12-31T23:59:59.500, 2025, 2025-12-31T23:59:59",
        "year, 2026-01-01T00:00:00.300, 2026, 2026-12-31T23:59:59",
        "month, 2026-02-28T23:59:59.500, 202602, 2026-02-28T23:59:59",
        "month, 2026-03-01T00:00:00.300, 202603, 2026-03-31T23:59:59",
        "day, 2026-10-12T23:59:59.900, 20261012, 2026-10-12T23:59:59",
        "day, 2026-10-13T00:00:00.100, 20261013, 2026-10-13T23:59:59",
    })
    void theKeyIsTheCurrentPeriodAndTheLastSecondStillBelongsToIt(String by, String now, String key, String end) {
        Instant instant = LocalDateTime.parse(now).toInstant(ZoneOffset.UTC);
        GroupedKey groupedKey = supplier(by, Clock.fixed(instant, ZoneOffset.UTC)).get();
        
        assertThat(groupedKey.getKey(), equalTo(key));
        long endSecond = LocalDateTime.parse(end).toEpochSecond(ZoneOffset.UTC);
        assertThat(groupedKey.getTtlAt(), equalTo(endSecond));
        // In the last second the group has no second left but has not ended: a segment fetched then lives until it ends.
        long nowSecond = instant.getEpochSecond();
        assertThat(Math.max(0, groupedKey.getTtlAt() - nowSecond), equalTo(endSecond - nowSecond));
    }
}
