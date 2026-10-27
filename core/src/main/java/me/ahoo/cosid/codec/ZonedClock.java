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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;

/**
 * Local date and time fields of epoch milliseconds in one zone, without {@code java.time} objects on the hot path: the
 * offset is cached for the span between two transitions of the zone, the civil date comes from integer arithmetic
 * (03-core-model.md §1, 10 §1.4).
 */
final class ZonedClock {
    static final long MILLIS_PER_DAY = 86_400_000L;
    private static final int DAYS_0000_TO_1970 = 719_468;
    private static final int DAYS_PER_ERA = 146_097;

    private final ZoneId zone;
    private final ZoneRules rules;
    /** Immutable, so a racy read sees a whole span. */
    private Span span;

    ZonedClock(ZoneId zone) {
        this.zone = zone;
        this.rules = zone.getRules();
        this.span = span(0);
    }

    /**
     * The local milliseconds, since 1970-01-01T00:00 local time, of {@code epochMillis}.
     */
    long toLocal(long epochMillis) {
        Span current = span;
        if (epochMillis < current.from || epochMillis >= current.until) {
            current = span(epochMillis);
            span = current;
        }
        return epochMillis + current.offsetMillis;
    }

    /**
     * The epoch milliseconds of local milliseconds; in a gap or an overlap, as {@link ZonedDateTime#of} resolves it.
     */
    long toEpoch(long localMillis) {
        Span current = span;
        long candidate = localMillis - current.offsetMillis;
        // A day away from a transition, the local time has exactly one instant.
        if (candidate > saturatedAdd(current.from, MILLIS_PER_DAY) && candidate < saturatedAdd(current.until, -MILLIS_PER_DAY)) {
            return candidate;
        }
        LocalDateTime local = LocalDateTime.ofEpochSecond(Math.floorDiv(localMillis, 1000), Math.floorMod(localMillis, 1000) * 1_000_000,
            ZoneOffset.UTC);
        return ZonedDateTime.of(local, zone).toInstant().toEpochMilli();
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? (a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE) : sum;
    }

    private Span span(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        ZoneOffset offset = rules.getOffset(instant);
        long offsetMillis = offset.getTotalSeconds() * 1000L;
        if (rules.isFixedOffset()) {
            return new Span(Long.MIN_VALUE, Long.MAX_VALUE, offsetMillis);
        }
        ZoneOffsetTransition next = rules.nextTransition(instant);
        long until = next == null ? Long.MAX_VALUE : next.toEpochSecond() * 1000;
        ZoneOffsetTransition previous = rules.previousTransition(instant);
        long from = previous == null ? Long.MIN_VALUE : previous.toEpochSecond() * 1000;
        if (from > epochMillis || from != Long.MIN_VALUE && !rules.getOffset(Instant.ofEpochMilli(from)).equals(offset)) {
            from = epochMillis;
        }
        if (until <= epochMillis) {
            until = epochMillis + 1;
        }
        return new Span(from, until, offsetMillis);
    }

    /**
     * The civil date of a day count since 1970-01-01, packed as {@code year << 9 | month << 5 | day} (H. Hinnant's
     * algorithm).
     */
    static long civil(long epochDay) {
        long z = epochDay + DAYS_0000_TO_1970;
        long era = Math.floorDiv(z, DAYS_PER_ERA);
        long dayOfEra = z - era * DAYS_PER_ERA;
        long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        long mp = (5 * dayOfYear + 2) / 153;
        long day = dayOfYear - (153 * mp + 2) / 5 + 1;
        long month = mp < 10 ? mp + 3 : mp - 9;
        long year = yearOfEra + era * 400 + (month <= 2 ? 1 : 0);
        return year << 9 | month << 5 | day;
    }

    /**
     * The day count since 1970-01-01 of a valid civil date.
     */
    static long epochDay(long year, int month, int day) {
        long y = month <= 2 ? year - 1 : year;
        long era = Math.floorDiv(y, 400);
        long yearOfEra = y - era * 400;
        long dayOfYear = (153L * (month > 2 ? month - 3 : month + 9) + 2) / 5 + day - 1;
        long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * DAYS_PER_ERA + dayOfEra - DAYS_0000_TO_1970;
    }

    static int lengthOfMonth(long year, int month) {
        return switch (month) {
            case 2 -> (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    /**
     * Local milliseconds from fields, validated.
     *
     * @throws IllegalArgumentException if a field is out of range
     */
    static long localMillis(long year, int month, int day, int hour, int minute, int second, int millis) {
        if (month < 1 || month > 12 || day < 1 || day > lengthOfMonth(year, month) || hour > 23 || minute > 59 || second > 59 || millis > 999) {
            throw new IllegalArgumentException("Invalid date-time " + year + "-" + month + "-" + day + "T" + hour + ":" + minute + ":" + second
                + "." + millis);
        }
        return epochDay(year, month, day) * MILLIS_PER_DAY + ((hour * 60L + minute) * 60 + second) * 1000 + millis;
    }

    /**
     * Writes {@code value} as exactly {@code width} decimal digits ending before {@code end}.
     */
    static void writeDecimal(byte[] buf, int end, int width, long value) {
        for (int i = end - 1; i >= end - width; i--) {
            buf[i] = (byte) ('0' + value % 10);
            value /= 10;
        }
    }

    /**
     * Reads exactly {@code width} decimal digits at {@code from}.
     *
     * @throws IllegalArgumentException if a character is not a digit
     */
    static int readDecimal(String text, int from, int width) {
        int value = 0;
        for (int i = from; i < from + width; i++) {
            int digit = text.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                throw new IllegalArgumentException("[" + text + "] has a non-digit at " + i);
            }
            value = value * 10 + digit;
        }
        return value;
    }

    private record Span(long from, long until, long offsetMillis) {
    }
}
