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

package me.ahoo.cosid.converter;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.converter.DatePrefixConverterStat;

import org.jspecify.annotations.NonNull;

import java.text.ParsePosition;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ID converter that prepends a date prefix to the ID string.
 *
 * <p>This converter wraps another IdConverter and adds a date-based prefix,
 * useful for time-based partitioning or bucketing of IDs.
 *
 * @author ahoo wang
 */
public class DatePrefixIdConverter implements IdConverter, Decorator<IdConverter> {
    private final String pattern;
    private final DateTimeFormatter formatter;
    private final String delimiter;
    private final IdConverter actual;
    /**
     * The length of every formatted date, or {@code -1} when it varies (one-letter fields, text months).
     */
    private final int fixedDateLength;

    /**
     * Creates a new DatePrefixIdConverter.
     *
     * @param pattern  date format pattern (see {@link DateTimeFormatter})
     * @param delimiter delimiter between prefix and ID
     * @param actual   the underlying converter to wrap
     */
    public DatePrefixIdConverter(String pattern, String delimiter, IdConverter actual) {
        this.pattern = pattern;
        this.formatter = DateTimeFormatter.ofPattern(pattern);
        this.delimiter = delimiter;
        this.actual = actual;
        this.fixedDateLength = fixedLength(formatter);
    }
    
    /**
     * The length of the dates the formatter writes when it is always the same: dates that make each field as short or
     * as long as it gets (single and double digits, every month and day of the week, morning and afternoon).
     */
    private static int fixedLength(DateTimeFormatter formatter) {
        int length = -1;
        for (int month = 1; month <= 12; month++) {
            for (int day : new int[] {1, 9, 10, 28}) {
                for (int hour : new int[] {0, 9, 13, 23}) {
                    int formatted = formatter.format(LocalDateTime.of(2000 + month, month, day, hour, month % 10, day % 10, 0)).length();
                    if (length == -1) {
                        length = formatted;
                    } else if (length != formatted) {
                        return -1;
                    }
                }
            }
        }
        return length;
    }


    @Override
    public @NonNull String asString(long id) {
        return now().format(formatter) + delimiter + actual.asString(id);
    }
    
    @Override
    public @NonNull String asString(long id, @NonNull GroupedKey group) {
        return now().format(formatter) + delimiter + actual.asString(id, group);
    }

    @Override
    public long asLong(@NonNull String idString) {
        if (fixedDateLength >= 0) {
            // A fixed-length date is cut off by its length: parsing a date directly followed by digits (yyyyMMdd and an
            // empty delimiter) would take the id's first digits as part of the year.
            int idStart = fixedDateLength + delimiter.length();
            if (idString.length() < idStart || !idString.startsWith(delimiter, fixedDateLength)) {
                throw new IllegalArgumentException("Id [" + idString + "] does not start with a date of pattern [" + pattern + "] and the delimiter ["
                    + delimiter + "].");
            }
            return actual.asLong(idString.substring(idStart));
        }
        // A date whose length varies (one-letter fields, text months) is parsed to find where the id starts.
        ParsePosition position = new ParsePosition(0);
        formatter.parseUnresolved(idString, position);
        int dateEnd = position.getIndex();
        if (position.getErrorIndex() >= 0 || !idString.startsWith(delimiter, dateEnd)) {
            throw new IllegalArgumentException("Id [" + idString + "] does not start with a date of pattern [" + pattern + "] and the delimiter ["
                + delimiter + "].");
        }
        return actual.asLong(idString.substring(dateEnd + delimiter.length()));
    }

    @Override
    public @NonNull IdConverter getActual() {
        return actual;
    }

    @Override
    public Stat stat() {
        return new DatePrefixConverterStat(getClass().getSimpleName(), pattern, actual.stat());
    }

    LocalDateTime now() {
        return LocalDateTime.now();
    }
}
