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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;


public class YearMonthGroupBySupplier extends AbstractDateGroupBySupplier<YearMonth> {
    public YearMonthGroupBySupplier(DateTimeFormatter formatter) {
        super(formatter);
    }

    public YearMonthGroupBySupplier(String pattern) {
        this(DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    YearMonth now() {
        return YearMonth.now();
    }

    @Override
    LocalDateTime lastTimestamp(YearMonth date) {
        LocalDate lastDate = LocalDate.MAX.withYear(date.getYear()).withMonth(date.getMonthValue());
        return LocalDateTime.of(lastDate, LocalTime.MAX);
    }
}
