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

import me.ahoo.cosid.internal.PatternPeriod;
import me.ahoo.cosid.segment.Period;

import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.Temporal;
import java.util.Locale;
import java.util.Objects;

/**
 * The keys of a preset period written in a pattern of their own (04-segment.md §3, D51): with {@code yyMMdd} the key
 * {@code 20261124} reads {@code 261124}. Only the text changes; the keys, and so the store keys, keep the preset's
 * pattern.
 *
 * <p>The pattern is read strictly in {@link Locale#ROOT}, {@code y} as the proleptic year {@code u}, so {@code yy} reads
 * 2000 to 2099. It must tell exactly the period's date: a {@code YEAR_MONTH_DAY} pattern needs the year, month and day,
 * and no period pattern takes a finer field.
 */
final class PeriodPattern {
    private final PatternPeriod period;
    private final String pattern;
    private final DateTimeFormatter formatter;
    /** The length of every text of the pattern, or -1 when it varies (text fields, unpadded numbers). */
    private final int fixedLength;

    private PeriodPattern(PatternPeriod period, String pattern, DateTimeFormatter formatter, int fixedLength) {
        this.period = period;
        this.pattern = pattern;
        this.formatter = formatter;
        this.fixedLength = fixedLength;
    }

    /**
     * The pattern for the keys of {@code period}.
     *
     * @throws IllegalArgumentException if {@code period} is not a preset, or {@code pattern} does not tell exactly its date
     */
    static PeriodPattern of(Period period, String pattern) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(pattern, "pattern");
        if (!(period instanceof PatternPeriod preset)) {
            throw new IllegalArgumentException("A period pattern needs a preset period (YEAR, YEAR_MONTH or YEAR_MONTH_DAY), whose keys it can read: " + period);
        }
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be empty");
        }
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(proleptic(pattern), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("[" + pattern + "] is not a date pattern: " + e.getMessage(), e);
        }
        int length = -1;
        boolean fixed = true;
        for (int month = 1; month <= 12; month++) {
            for (int day : new int[] {1, 9, 10, 28}) {
                Temporal date = preset.dateOf(LocalDate.of(2000 + month, month, day));
                String text = write(preset, pattern, formatter, date);
                if (!date.equals(read(preset, pattern, formatter, text))) {
                    throw new IllegalArgumentException("[" + pattern + "] reads [" + text + "] back as another " + preset.name() + " period than " + date);
                }
                if (length >= 0 && text.length() != length) {
                    fixed = false;
                }
                length = text.length();
            }
        }
        return new PeriodPattern(preset, pattern, formatter, fixed ? length : -1);
    }

    private static String write(PatternPeriod period, String pattern, DateTimeFormatter formatter, Temporal date) {
        try {
            return formatter.format(date);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("[" + pattern + "] has a field finer than a " + period.name() + " period: " + e.getMessage(), e);
        }
    }

    private static Temporal read(PatternPeriod period, String pattern, DateTimeFormatter formatter, String text) {
        try {
            return period.dateOf(formatter.parse(text));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("[" + pattern + "] does not tell a whole " + period.name() + " period: " + e.getMessage(), e);
        }
    }

    /** {@code y} (year of era, which strict resolving leaves alone without an era) as {@code u}, outside quoted text. */
    private static String proleptic(String pattern) {
        StringBuilder converted = new StringBuilder(pattern.length());
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            }
            converted.append(!quoted && c == 'y' ? 'u' : c);
        }
        return converted.toString();
    }

    String pattern() {
        return pattern;
    }

    /** The length of every text of the pattern, or -1 when it varies. */
    int fixedLength() {
        return fixedLength;
    }

    /**
     * The text of a period key.
     *
     * @throws IllegalArgumentException if {@code key} is not a key of the period
     */
    String text(String key) {
        return formatter.format(period.dateOf(key));
    }

    /**
     * Where the text of a key at the start of {@code text} ends, for a pattern of varying length; a fixed-length one is
     * cut at {@link #fixedLength()}, since adjacent numbers without fixed widths parse greedily.
     *
     * @return the index after the key's text, or -1 if {@code text} does not start with one
     */
    int end(String text) {
        ParsePosition position = new ParsePosition(0);
        formatter.parseUnresolved(text, position);
        return position.getErrorIndex() >= 0 ? -1 : position.getIndex();
    }

    /**
     * The period key a text of the pattern names; the inverse of {@link #text(String)}.
     *
     * @throws IllegalArgumentException if {@code text} is not the text of a key, another spelling of one included
     */
    String key(String text) {
        try {
            Temporal date = period.dateOf(formatter.parse(text));
            // Refuses another spelling of the same period, such as an unpadded number for a padded one.
            if (formatter.format(date).equals(text)) {
                return period.keyOfDate(date);
            }
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("[" + text + "] is not a " + period.name() + " period of the pattern [" + pattern + "]", e);
        }
        throw new IllegalArgumentException("[" + text + "] is not a " + period.name() + " period of the pattern [" + pattern + "]");
    }
}
