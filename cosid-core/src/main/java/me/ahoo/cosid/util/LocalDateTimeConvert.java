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

package me.ahoo.cosid.util;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * type Convert tool class of {@link LocalDateTime}.
 *
 * @author ahoo wang
 */
@ThreadSafe
public final class LocalDateTimeConvert {

    public static LocalDateTime fromDate(Date date, ZoneId zoneId) {
        return LocalDateTime.ofInstant(date.toInstant(), zoneId);
    }

    public static LocalDateTime fromTimestamp(Long timestamp, ZoneId zoneId) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
    }

    public static LocalDateTime fromTimestampSecond(Long timestamp, ZoneId zoneId) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zoneId);
    }

    /**
     * convert {@link String} to {@link LocalDateTime}.
     *
     * @param dateTime string type date time
     * @param dateTimeFormatter date time formatter
     * @return LocalDateTime from string
     */
    public static LocalDateTime fromString(String dateTime, DateTimeFormatter dateTimeFormatter) {
        TemporalAccessor temporalAccessor = dateTimeFormatter.parseBest(dateTime, LocalDateTime::from, LocalDate::from);
        if (temporalAccessor instanceof LocalDateTime) {
            return (LocalDateTime) temporalAccessor;
        }
        return LocalDateTime.of((LocalDate) temporalAccessor, LocalTime.MIN);
    }
}
