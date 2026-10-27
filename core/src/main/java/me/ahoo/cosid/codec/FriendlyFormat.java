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

import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * The friendly text of time-based id parts, {@code yyyyMMddHHmmss[SSS]-<node>-<seq>} ({@code SSS} for millisecond
 * layouts), shared by {@link Friendly} and {@link PartsCodec#friendly}. Writes bytes at precomputed field widths.
 */
final class FriendlyFormat {
    static final char DELIMITER = '-';
    private static final int MAX_DECIMAL_DIGITS = 19;
    /** Far beyond the year 9999, and far from overflowing with any epoch. */
    private static final long MAX_TEXT_MILLIS = 1L << 52;

    private final Layout layout;
    private final ZonedClock clock;
    private final boolean padStart;
    private final long epochMillis;
    private final long unitMillis;
    private final boolean millis;
    private final int dateTimeWidth;
    private final long maxTime;
    private final long maxNode;
    private final long maxSeq;
    private final int nodeWidth;
    private final int seqWidth;

    FriendlyFormat(Layout layout, ZoneId zone, boolean padStart) {
        this.layout = layout;
        this.clock = new ZonedClock(zone);
        this.padStart = padStart;
        this.epochMillis = layout.epoch().toEpochMilli();
        this.unitMillis = layout.unit().getDuration().toMillis();
        this.millis = layout.unit() == ChronoUnit.MILLIS;
        this.dateTimeWidth = millis ? 17 : 14;
        this.maxTime = ~(-1L << layout.timeBits());
        this.maxNode = ~(-1L << layout.nodeBits());
        this.maxSeq = ~(-1L << layout.seqBits());
        this.nodeWidth = decimalWidth(maxNode);
        this.seqWidth = decimalWidth(maxSeq);
    }

    static int decimalWidth(long max) {
        int width = 1;
        for (long rest = max / 10; rest > 0; rest /= 10) {
            width++;
        }
        return width;
    }

    String format(long time, long node, long seq) {
        if (time < 0 || time > maxTime || time > MAX_TEXT_MILLIS / unitMillis || node < 0 || node > maxNode || seq < 0 || seq > maxSeq) {
            throw new IllegalArgumentException("Parts (" + time + ", " + node + ", " + seq + ") do not fit layout " + layout);
        }
        int nodeDigits = padStart ? nodeWidth : decimalWidth(node);
        int seqDigits = padStart ? seqWidth : decimalWidth(seq);
        byte[] buf = new byte[dateTimeWidth + 2 + nodeDigits + seqDigits];
        long local = clock.toLocal(epochMillis + time * unitMillis);
        long epochDay = Math.floorDiv(local, ZonedClock.MILLIS_PER_DAY);
        final long ofDay = Math.floorMod(local, ZonedClock.MILLIS_PER_DAY);
        long date = ZonedClock.civil(epochDay);
        long year = date >> 9;
        if (year < 0 || year > 9999) {
            throw new IllegalArgumentException("Time " + time + " has a year outside [0, 9999]: " + year);
        }
        ZonedClock.writeDecimal(buf, 4, 4, year);
        ZonedClock.writeDecimal(buf, 6, 2, (date >> 5) & 0xF);
        ZonedClock.writeDecimal(buf, 8, 2, date & 0x1F);
        long seconds = ofDay / 1000;
        ZonedClock.writeDecimal(buf, 10, 2, seconds / 3600);
        ZonedClock.writeDecimal(buf, 12, 2, seconds / 60 % 60);
        ZonedClock.writeDecimal(buf, 14, 2, seconds % 60);
        if (millis) {
            ZonedClock.writeDecimal(buf, 17, 3, ofDay % 1000);
        }
        int at = dateTimeWidth;
        buf[at++] = DELIMITER;
        ZonedClock.writeDecimal(buf, at + nodeDigits, nodeDigits, node);
        at += nodeDigits;
        buf[at++] = DELIMITER;
        ZonedClock.writeDecimal(buf, at + seqDigits, seqDigits, seq);
        return new String(buf, StandardCharsets.ISO_8859_1);
    }

    /**
     * Parses friendly text, padded or not.
     *
     * @throws IllegalArgumentException if the text is not friendly text of the layout
     */
    IdParts parse(String text) {
        int length = text.length();
        if (length < dateTimeWidth + 4 || text.charAt(dateTimeWidth) != DELIMITER) {
            throw invalid(text);
        }
        int year = ZonedClock.readDecimal(text, 0, 4);
        int month = ZonedClock.readDecimal(text, 4, 2);
        int day = ZonedClock.readDecimal(text, 6, 2);
        int hour = ZonedClock.readDecimal(text, 8, 2);
        int minute = ZonedClock.readDecimal(text, 10, 2);
        int second = ZonedClock.readDecimal(text, 12, 2);
        int milli = millis ? ZonedClock.readDecimal(text, 14, 3) : 0;
        long epoch = clock.toEpoch(ZonedClock.localMillis(year, month, day, hour, minute, second, milli));
        long sinceEpoch = epoch - epochMillis;
        if (sinceEpoch < 0 || sinceEpoch % unitMillis != 0 || sinceEpoch / unitMillis > maxTime) {
            throw new IllegalArgumentException("[" + text + "] has a time outside layout " + layout);
        }
        int nodeStart = dateTimeWidth + 1;
        int nodeEnd = text.indexOf(DELIMITER, nodeStart);
        if (nodeEnd < 0) {
            throw invalid(text);
        }
        long node = readNumber(text, nodeStart, nodeEnd, maxNode);
        long seq = readNumber(text, nodeEnd + 1, length, maxSeq);
        return new IdParts(sinceEpoch / unitMillis, (int) node, seq);
    }

    private static long readNumber(String text, int from, int to, long max) {
        int width = to - from;
        if (width < 1 || width > MAX_DECIMAL_DIGITS) {
            throw invalid(text);
        }
        long value = 0;
        for (int i = from; i < to; i++) {
            int digit = text.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                throw invalid(text);
            }
            value = value * 10 + digit;
        }
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("[" + text + "] has a part above " + max);
        }
        return value;
    }

    private static IllegalArgumentException invalid(String text) {
        return new IllegalArgumentException("[" + text + "] is not friendly id text");
    }
}
