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

import me.ahoo.cosid.internal.PatternPeriod;

import java.time.Instant;
import java.time.ZoneId;

/**
 * The period of a periodic segment (04-segment.md §3): presets {@link #YEAR}, {@link #YEAR_MONTH} and
 * {@link #YEAR_MONTH_DAY}, or your own.
 */
public interface Period {
    /** {@code "2026"}. */
    Period YEAR = PatternPeriod.YEAR;
    /** {@code "202610"}. */
    Period YEAR_MONTH = PatternPeriod.YEAR_MONTH;
    /** {@code "20261026"}. */
    Period YEAR_MONTH_DAY = PatternPeriod.YEAR_MONTH_DAY;

    /**
     * The key of the period {@code instant} falls in.
     *
     * @param instant the instant
     * @param zone    the time zone the periods follow
     * @return the period key, such as {@code "20261026"}
     */
    String keyOf(Instant instant, ZoneId zone);

    /**
     * The end of the period, exclusive.
     *
     * @param key  the period key
     * @param zone the time zone the periods follow
     * @return the first instant after the period
     */
    Instant endOf(String key, ZoneId zone);
}
