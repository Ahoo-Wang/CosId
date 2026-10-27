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

package me.ahoo.cosid.sharding;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * Converts the common date and time types, epoch timestamps and formatted text to a {@link LocalDateTime}.
 */
public class StandardLocalDateTimeConvertor implements LocalDateTimeConvertor {
    private final ZoneId zoneId;

    private final boolean isSecondTs;

    private final DateTimeFormatter dateTimeFormatter;

    /**
     * Creates the convertor.
     *
     * @param zoneId            the zone of instants, dates and timestamps
     * @param isSecondTs        whether a {@code Long} counts seconds rather than milliseconds since the Unix epoch
     * @param dateTimeFormatter the format of text values, a date-time or a date
     */
    public StandardLocalDateTimeConvertor(ZoneId zoneId, boolean isSecondTs, DateTimeFormatter dateTimeFormatter) {
        this.zoneId = zoneId;
        this.isSecondTs = isSecondTs;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    @Override
    public LocalDateTime toLocalDateTime(Comparable<?> value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toLocalDateTime();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toLocalDateTime();
        }
        if (value instanceof Instant) {
            return LocalDateTime.ofInstant((Instant) value, zoneId);
        }
        if (value instanceof LocalDate) {
            return LocalDateTime.of((LocalDate) value, LocalTime.MIN);
        }
        if (value instanceof Date) {
            return LocalDateTime.ofInstant(((Date) value).toInstant(), zoneId);
        }
        if (value instanceof YearMonth) {
            YearMonth yearMonth = (YearMonth) value;
            return LocalDateTime.of(yearMonth.getYear(), yearMonth.getMonthValue(), 1, 0, 0);
        }
        if (value instanceof Year) {
            return LocalDateTime.of(((Year) value).getValue(), 1, 1, 0, 0);
        }
        if (value instanceof Long) {
            return LocalDateTime.ofInstant(isSecondTs ? Instant.ofEpochSecond((Long) value) : Instant.ofEpochMilli((Long) value), zoneId);
        }
        if (value instanceof String) {
            TemporalAccessor parsed = dateTimeFormatter.parseBest((String) value, LocalDateTime::from, LocalDate::from);
            return parsed instanceof LocalDateTime dateTime ? dateTime : LocalDateTime.of((LocalDate) parsed, LocalTime.MIN);
        }
        throw new IllegalArgumentException("Unsupported sharding value type `" + value + "`.");
    }
}
