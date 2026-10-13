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

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DatePrefixIdConverter implements IdConverter, Decorator<IdConverter> {
    private final String pattern;
    private final DateTimeFormatter formatter;
    private final String delimiter;
    private final IdConverter actual;
    /**
     * The shortest and the longest formatted date: equal for a fixed-length date.
     */
    private final int minDateLength;
    private final int maxDateLength;

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
        int[] lengths = dateLengths(formatter);
        this.minDateLength = lengths[0];
        this.maxDateLength = lengths[1];
        if (minDateLength != maxDateLength && delimiter.isEmpty() && log.isWarnEnabled()) {
            log.warn("Date prefix pattern [{}] formats dates of {} to {} characters and has no delimiter: where the id starts is ambiguous. "
                + "Set a delimiter, or use a fixed-length pattern (yyyyMMdd).", pattern, minDateLength, maxDateLength);
        }
    }
    
    /**
     * The shortest and longest dates the formatter writes, over dates that make each field as short or as long as it gets:
     * single and double digits, every month and day of the week, morning and afternoon, whole and fractional seconds.
     */
    private static int[] dateLengths(DateTimeFormatter formatter) {
        int min = Integer.MAX_VALUE;
        int max = 0;
        int[] days = {1, 9, 10, 28};
        int[] hours = {0, 9, 13, 23};
        int[] minutesAndSeconds = {0, 9, 10, 59};
        int[] nanos = {0, 999_999_999};
        for (int month = 1; month <= 12; month++) {
            for (int day : days) {
                for (int hour : hours) {
                    for (int minute : minutesAndSeconds) {
                        for (int second : minutesAndSeconds) {
                            for (int nano : nanos) {
                                int length = formatter.format(LocalDateTime.of(2000 + month, month, day, hour, minute, second, nano)).length();
                                min = Math.min(min, length);
                                max = Math.max(max, length);
                            }
                        }
                    }
                }
            }
        }
        return new int[] {min, max};
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
        return actual.asLong(idString.substring(idStart(idString)));
    }
    
    /**
     * Where the id starts after the date and the delimiter. A fixed-length date is cut off by its length: parsing a date
     * directly followed by digits would take them as part of a 4-digit year. Otherwise the delimiter marks the end of the
     * date, searched from the shortest date; without one, the date is parsed (ambiguous, warned at construction).
     */
    private int idStart(String idString) {
        if (minDateLength == maxDateLength) {
            if (idString.length() >= minDateLength + delimiter.length() && idString.startsWith(delimiter, minDateLength)) {
                return minDateLength + delimiter.length();
            }
        } else if (!delimiter.isEmpty()) {
            // The date can contain the delimiter itself (MMMM-d and "-"): of the delimiters within the date's lengths, take
            // the rightmost one before which the whole text parses as a date. Some dates do not parse back (a 4-digit year
            // before a text month in Chinese, "202610月13"): then the first delimiter after the shortest date.
            int last = Math.min(maxDateLength, idString.length() - delimiter.length());
            int first = -1;
            for (int delimiterIndex = idString.lastIndexOf(delimiter, last); delimiterIndex >= minDateLength;
                 delimiterIndex = idString.lastIndexOf(delimiter, delimiterIndex - 1)) {
                if (isDate(idString, delimiterIndex)) {
                    return delimiterIndex + delimiter.length();
                }
                first = delimiterIndex;
            }
            if (first >= 0) {
                return first + delimiter.length();
            }
        } else {
            ParsePosition position = new ParsePosition(0);
            formatter.parseUnresolved(idString, position);
            if (position.getErrorIndex() < 0) {
                return position.getIndex();
            }
        }
        throw new IllegalArgumentException("Id [" + idString + "] does not start with a date of pattern [" + pattern + "] and the delimiter ["
            + delimiter + "].");
    }

    private boolean isDate(String idString, int end) {
        ParsePosition position = new ParsePosition(0);
        formatter.parseUnresolved(idString.substring(0, end), position);
        return position.getErrorIndex() < 0 && position.getIndex() == end;
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
