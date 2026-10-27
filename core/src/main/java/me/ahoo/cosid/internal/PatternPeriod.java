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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.segment.Period;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * The preset periods: calendar years, months and days in a time zone, keyed by their {@link #pattern()}.
 */
public enum PatternPeriod implements Period {
    YEAR("yyyy"),
    YEAR_MONTH("yyyyMM"),
    YEAR_MONTH_DAY("yyyyMMdd");

    private final String pattern;
    private final DateTimeFormatter formatter;

    PatternPeriod(String pattern) {
        this.pattern = pattern;
        // uuuu (proleptic year) with strict resolving: a key such as "20261332" is refused, not adjusted.
        this.formatter = DateTimeFormatter.ofPattern(pattern.replace('y', 'u')).withResolverStyle(ResolverStyle.STRICT);
    }

    public String pattern() {
        return pattern;
    }

    @Override
    public String keyOf(Instant instant, ZoneId zone) {
        return formatter.format(LocalDate.ofInstant(instant, zone));
    }

    @Override
    public Instant endOf(String key, ZoneId zone) {
        try {
            LocalDate next = switch (this) {
                case YEAR -> Year.parse(key, formatter).plusYears(1).atDay(1);
                case YEAR_MONTH -> YearMonth.parse(key, formatter).plusMonths(1).atDay(1);
                case YEAR_MONTH_DAY -> LocalDate.parse(key, formatter).plusDays(1);
            };
            return next.atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("[" + key + "] is not a " + name() + " period key (" + pattern + ")", e);
        }
    }
}
