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

package me.ahoo.cosid.codec;

import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The date text of a {@link DatePrefix} pattern. A pattern of fixed-width numeric fields ({@code yyyy}, {@code uuuu},
 * {@code yy}, {@code MM}, {@code dd}, {@code HH}, {@code mm}, {@code ss}, {@code SSS}) and literal non-letters is
 * written byte by byte at a fixed width; any other pattern goes through {@link DateTimeFormatter}.
 */
abstract sealed class DateText {
    static DateText of(String pattern, ZoneId zone) {
        List<Field> fields = Fixed.compile(pattern);
        return fields == null ? new Formatted(pattern, zone) : new Fixed(fields, zone);
    }

    abstract String format(long epochMillis);

    /**
     * Where the date at the start of {@code text} ends.
     *
     * @return the index after the date, or -1 if the text does not start with a date of the pattern
     */
    abstract int end(String text);

    private enum Kind { LITERAL, YEAR, YEAR_OF_CENTURY, MONTH, DAY, HOUR, MINUTE, SECOND, MILLI }

    private record Field(Kind kind, int width, char literal) {
    }

    private static final class Fixed extends DateText {
        private final Field[] fields;
        private final int width;
        private final ZonedClock clock;

        Fixed(List<Field> fields, ZoneId zone) {
            this.fields = fields.toArray(new Field[0]);
            this.width = fields.stream().mapToInt(Field::width).sum();
            this.clock = new ZonedClock(zone);
        }

        /**
         * The fields of a fixed-width pattern, or {@code null}.
         */
        static List<Field> compile(String pattern) {
            List<Field> fields = new ArrayList<>();
            for (int i = 0; i < pattern.length(); ) {
                char c = pattern.charAt(i);
                int run = 1;
                while (i + run < pattern.length() && pattern.charAt(i + run) == c) {
                    run++;
                }
                if (!Character.isLetter(c)) {
                    if (c == '\'' || c > 0x7F || c == '[' || c == ']' || c == '{' || c == '}' || c == '#') {
                        return null;
                    }
                    for (int k = 0; k < run; k++) {
                        fields.add(new Field(Kind.LITERAL, 1, c));
                    }
                } else {
                    Kind kind = switch (c + ":" + run) {
                        case "y:4", "u:4" -> Kind.YEAR;
                        case "y:2", "u:2" -> Kind.YEAR_OF_CENTURY;
                        case "M:2" -> Kind.MONTH;
                        case "d:2" -> Kind.DAY;
                        case "H:2" -> Kind.HOUR;
                        case "m:2" -> Kind.MINUTE;
                        case "s:2" -> Kind.SECOND;
                        case "S:3" -> Kind.MILLI;
                        default -> null;
                    };
                    if (kind == null) {
                        return null;
                    }
                    fields.add(new Field(kind, run, ' '));
                }
                i += run;
            }
            return fields;
        }

        @Override
        String format(long epochMillis) {
            long local = clock.toLocal(epochMillis);
            long ofDay = Math.floorMod(local, ZonedClock.MILLIS_PER_DAY);
            long date = ZonedClock.civil(Math.floorDiv(local, ZonedClock.MILLIS_PER_DAY));
            long year = date >> 9;
            if (year < 0 || year > 9999) {
                throw new IllegalArgumentException("Time " + epochMillis + " has a year outside [0, 9999]: " + year);
            }
            byte[] buf = new byte[width];
            int end = 0;
            for (Field field : fields) {
                end += field.width;
                long value = switch (field.kind) {
                    case LITERAL -> -1;
                    case YEAR -> year;
                    case YEAR_OF_CENTURY -> year % 100;
                    case MONTH -> (date >> 5) & 0xF;
                    case DAY -> date & 0x1F;
                    case HOUR -> ofDay / 3_600_000;
                    case MINUTE -> ofDay / 60_000 % 60;
                    case SECOND -> ofDay / 1000 % 60;
                    case MILLI -> ofDay % 1000;
                };
                if (field.kind == Kind.LITERAL) {
                    buf[end - 1] = (byte) field.literal;
                } else {
                    ZonedClock.writeDecimal(buf, end, field.width, value);
                }
            }
            return new String(buf, StandardCharsets.ISO_8859_1);
        }

        @Override
        int end(String text) {
            if (text.length() < width) {
                return -1;
            }
            int at = 0;
            for (Field field : fields) {
                for (int k = 0; k < field.width; k++, at++) {
                    char c = text.charAt(at);
                    if (field.kind == Kind.LITERAL ? c != field.literal : c < '0' || c > '9') {
                        return -1;
                    }
                }
            }
            return width;
        }
    }

    private static final class Formatted extends DateText {
        private final DateTimeFormatter formatter;
        /** The length of every date of the pattern, or -1 when it varies (text fields, unpadded numbers). */
        private final int fixedLength;

        Formatted(String pattern, ZoneId zone) {
            this.formatter = DateTimeFormatter.ofPattern(pattern).withZone(zone);
            this.fixedLength = fixedLength(formatter);
        }

        private static int fixedLength(DateTimeFormatter formatter) {
            int length = -1;
            for (int month = 1; month <= 12; month++) {
                for (int day : new int[] {1, 10, 28}) {
                    for (int hour : new int[] {0, 9, 13}) {
                        String text = formatter.format(LocalDateTime.of(2000 + month, month, day, hour, hour, hour, hour * 1_000_000)
                            .atZone(ZoneOffset.UTC));
                        if (length >= 0 && text.length() != length) {
                            return -1;
                        }
                        length = text.length();
                    }
                }
            }
            return length;
        }

        @Override
        String format(long epochMillis) {
            return formatter.format(Instant.ofEpochMilli(epochMillis));
        }

        @Override
        int end(String text) {
            // Adjacent numbers without fixed widths parse greedily, so a fixed-length date is cut off before parsing.
            String date = fixedLength >= 0 && text.length() >= fixedLength ? text.substring(0, fixedLength) : text;
            ParsePosition position = new ParsePosition(0);
            formatter.parseUnresolved(date, position);
            if (position.getErrorIndex() >= 0 || fixedLength >= 0 && position.getIndex() != fixedLength) {
                return -1;
            }
            return position.getIndex();
        }
    }
}
