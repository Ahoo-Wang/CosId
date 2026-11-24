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

import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.internal.PatternPeriod;
import me.ahoo.cosid.segment.Period;

import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Turns a {@link GroupedId} into text and back; grouped ids have their own codec, apart from {@link IdCodec}
 * (04-segment.md §3).
 */
public interface GroupedCodec {
    /**
     * Encodes a grouped id. An implementation is thread-safe: the generators of a runtime call it concurrently.
     *
     * @param id the id
     * @return the text, never null
     * @throws IllegalArgumentException if the codec cannot encode {@code id}, such as a value outside its range
     */
    String encode(GroupedId id);

    /**
     * Decodes text produced by {@link #encode(GroupedId)}.
     *
     * @param text the text
     * @return the id
     * @throws IllegalArgumentException if the text is not an encoded grouped id
     * @throws UnsupportedOperationException if the codec cannot decode at all, such as a date prefix without a delimiter
     */
    GroupedId decode(String text);

    /**
     * The period key, {@code delimiter}, then the value through {@code valueCodec}, such as {@code "20261026"} followed
     * by {@code Radix.of(10, 6, true)}.
     *
     * <p>Decoding splits the text at the first {@code delimiter}, so the period key must not contain it (the preset
     * keys are digits). Without a delimiter the text cannot be split, so {@link #decode} is not supported.
     *
     * @param delimiter  the text between the period and the value
     * @param valueCodec the codec of the value
     * @return the codec
     */
    static GroupedCodec datePrefix(String delimiter, IdCodec valueCodec) {
        Objects.requireNonNull(delimiter, "delimiter");
        Objects.requireNonNull(valueCodec, "valueCodec");
        return new GroupedCodec() {
            @Override
            public String encode(GroupedId id) {
                return id.period() + delimiter + valueCodec.encode(id.value());
            }

            @Override
            public GroupedId decode(String text) {
                if (delimiter.isEmpty()) {
                    throw new UnsupportedOperationException("A date prefix without a delimiter cannot be decoded: the period key has no known length");
                }
                int at = text.indexOf(delimiter);
                if (at <= 0) {
                    throw new IllegalArgumentException("[" + text + "] has no period followed by [" + delimiter + "]");
                }
                return new GroupedId(text.substring(0, at), valueCodec.decode(text.substring(at + delimiter.length())));
            }
        };
    }

    /**
     * {@link #datePrefix(String, IdCodec)} for the keys of {@code period}: with a preset period the key has a known
     * length, so the text decodes even without a delimiter (such as {@code "20261026000042"}). Decoding checks that the
     * key is one of {@code period}.
     *
     * @param period     the period of the keys
     * @param delimiter  the text between the period and the value; may be empty for a preset period
     * @param valueCodec the codec of the value
     * @return the codec
     */
    static GroupedCodec datePrefix(Period period, String delimiter, IdCodec valueCodec) {
        Objects.requireNonNull(period, "period");
        GroupedCodec split = datePrefix(delimiter, valueCodec);
        int keyLength = period instanceof PatternPeriod preset ? preset.pattern().length() : -1;
        return new GroupedCodec() {
            @Override
            public String encode(GroupedId id) {
                return split.encode(id);
            }

            @Override
            public GroupedId decode(String text) {
                GroupedId id;
                if (keyLength > 0) {
                    if (text.length() < keyLength + delimiter.length() || !text.startsWith(delimiter, keyLength)) {
                        throw new IllegalArgumentException("[" + text + "] has no " + keyLength + "-character period followed by [" + delimiter + "]");
                    }
                    id = new GroupedId(text.substring(0, keyLength), valueCodec.decode(text.substring(keyLength + delimiter.length())));
                } else {
                    id = split.decode(text);
                }
                // Refuses a key that is not one of the period's.
                period.endOf(id.period(), ZoneOffset.UTC);
                return id;
            }
        };
    }

    /**
     * The period key inside a decorated text: {@code inner} turns the value into text, the period key and
     * {@code delimiter} are prepended to it, and {@code outer} decorates the result. With
     * {@code outer = codec -> codec.withPrefix("BIZ-")} the id {@code (20261026, 42)} becomes
     * {@code "BIZ-20261026-000042"}: the period after a business prefix, as the 4.x {@code group-prefix} with
     * {@code before-prefix = true} (its default). {@code outer} may only decorate the text it is given (prefixes,
     * suffixes, date prefixes); the identity gives {@link #datePrefix(Period, String, IdCodec)}.
     *
     * @param period    the period of the keys
     * @param delimiter the text between the period and the value; may be empty for a preset period
     * @param inner     the codec of the value
     * @param outer     decorates the text that starts with the period key
     * @return the codec
     */
    static GroupedCodec datePrefix(Period period, String delimiter, IdCodec inner, UnaryOperator<IdCodec> outer) {
        return new PeriodWithin(period, delimiter, inner, outer);
    }

    /**
     * {@link #datePrefix(Period, String, IdCodec, UnaryOperator)} with the period key written in {@code pattern}: with
     * {@code yyMMdd} the id {@code (20261124, 1)} becomes {@code "261124-1"} (the 4.x {@code group.pattern}). Only the
     * text changes: {@link GroupedId#period()}, and so the store keys, keep the preset's pattern, and decoding gives
     * them back.
     *
     * <p>{@code pattern} is read strictly in {@link java.util.Locale#ROOT}, {@code y} as the proleptic year, so
     * {@code yy} reads 2000 to 2099. It must tell exactly the period's date: a {@code YEAR_MONTH_DAY} pattern needs the
     * year, month and day, and none takes a field finer than the period. A pattern of fixed length, such as
     * {@code yyMMdd}, decodes even without a delimiter; any other needs one.
     *
     * @param period    the period of the keys; a preset ({@link Period#YEAR}, {@link Period#YEAR_MONTH} or
     *                  {@link Period#YEAR_MONTH_DAY}), whose keys the codec can read
     * @param pattern   the date pattern of the key in the text, such as {@code yyMMdd}
     * @param delimiter the text between the period and the value; may be empty for a pattern of fixed length
     * @param inner     the codec of the value
     * @param outer     decorates the text that starts with the period key
     * @return the codec
     * @throws IllegalArgumentException if {@code period} is not a preset, or {@code pattern} does not tell exactly its date
     */
    static GroupedCodec datePrefix(Period period, String pattern, String delimiter, IdCodec inner, UnaryOperator<IdCodec> outer) {
        return new PeriodWithin(period, PeriodPattern.of(period, pattern), delimiter, inner, outer);
    }

    /**
     * The value alone, without its period: the 4.x {@code group-prefix} with {@code enabled = false}. The text does not
     * tell the period, so {@link #decode} is not supported.
     *
     * @param valueCodec the codec of the value
     * @return the codec
     */
    static GroupedCodec valueOnly(IdCodec valueCodec) {
        Objects.requireNonNull(valueCodec, "valueCodec");
        return new GroupedCodec() {
            @Override
            public String encode(GroupedId id) {
                return valueCodec.encode(id.value());
            }

            /**
             * Not supported: the period is not in the text.
             *
             * @throws UnsupportedOperationException always
             */
            @Override
            public GroupedId decode(String text) {
                throw new UnsupportedOperationException("[" + text + "] cannot be decoded: the period is not in the text of this generator's ids");
            }
        };
    }
}
