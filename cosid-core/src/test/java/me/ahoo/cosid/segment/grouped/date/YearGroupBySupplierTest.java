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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class YearGroupBySupplierTest {

    @Test
    void getShouldUseFormattedYearAndExpireAtEndOfYear() {
        FixedYearGroupBySupplier supplier = new FixedYearGroupBySupplier("yyyy", Year.of(2024));

        GroupedKey groupedKey = supplier.get();

        assertThat(groupedKey.getKey(), equalTo("2024"));
        assertThat(groupedKey.getTtlAt(), equalTo(epochSecond(LocalDateTime.of(LocalDate.of(2024, 12, 31), LocalTime.MAX))));
    }

    @Test
    void getShouldHonorCustomTwoDigitPattern() {
        FixedYearGroupBySupplier supplier = new FixedYearGroupBySupplier("yy", Year.of(2024));

        GroupedKey groupedKey = supplier.get();

        assertThat(groupedKey.getKey(), equalTo("24"));
    }

    private static long epochSecond(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000;
    }

    private static final class FixedYearGroupBySupplier extends YearGroupBySupplier {
        private final Year year;

        private FixedYearGroupBySupplier(String pattern, Year year) {
            super(DateTimeFormatter.ofPattern(pattern));
            this.year = year;
        }

        @Override
        Year now() {
            return year;
        }
    }
}
