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

import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * A date prefix, such as {@code 20261026-}, put before the text of another codec (03-core-model.md §1).
 *
 * <p>The date comes from the id itself ({@link Source#ID_TIME}, time-based ids only) or from the injected
 * {@link TimeSource} ({@link Source#NOW}, as 4.x segments did). {@link #of(String, String, Source)} describes the
 * prefix; {@link #apply(IdCodec, ZoneId, TimeSource, Optional)} puts it before a codec.
 */
public final class DatePrefix {
    /**
     * Where the date of the prefix comes from.
     */
    public enum Source {
        /** The time part of the id, through the generator's {@link Layout}. */
        ID_TIME,
        /** The injected {@link TimeSource} at encoding time. */
        NOW
    }

    private final String pattern;
    private final String delimiter;
    private final Source source;

    private DatePrefix(String pattern, String delimiter, Source source) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.delimiter = Objects.requireNonNull(delimiter, "delimiter");
        this.source = Objects.requireNonNull(source, "source");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be empty");
        }
    }

    /**
     * A date prefix.
     *
     * @param pattern   the date pattern, such as {@code yyyyMMdd}
     * @param delimiter the text between the date and the id
     * @param source    where the date comes from
     * @return the prefix
     */
    public static DatePrefix of(String pattern, String delimiter, Source source) {
        return new DatePrefix(pattern, delimiter, source);
    }

    public String pattern() {
        return pattern;
    }

    public String delimiter() {
        return delimiter;
    }

    public Source source() {
        return source;
    }

    /**
     * Puts this prefix before {@code inner}.
     *
     * @param inner  the codec of the id
     * @param zone   the time zone of the date
     * @param time   the time source, read for {@link Source#NOW}
     * @param layout the generator's layout, required for {@link Source#ID_TIME}
     * @return the prefixed codec
     */
    public IdCodec apply(IdCodec inner, ZoneId zone, TimeSource time, Optional<Layout> layout) {
        Objects.requireNonNull(inner, "inner");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(layout, "layout");
        DateText date = DateText.of(pattern, zone);
        if (source == Source.NOW) {
            return new Prefixed(date, delimiter, inner) {
                @Override
                long epochMillis(long id) {
                    return time.currentMillis();
                }
            };
        }
        Layout idLayout = layout.orElseThrow(() -> new IllegalArgumentException("A date prefix from the id time needs the layout of the id"));
        if (idLayout.totalBits() > Layout.LONG_BITS) {
            throw new IllegalArgumentException("A " + idLayout.totalBits() + "-bit layout does not fit a long id");
        }
        long epochMillis = idLayout.epoch().toEpochMilli();
        long unitMillis = idLayout.unit().getDuration().toMillis();
        int timeShift = idLayout.nodeBits() + idLayout.seqBits();
        return new Prefixed(date, delimiter, inner) {
            @Override
            long epochMillis(long id) {
                if (id < 0) {
                    throw new IllegalArgumentException("id must not be negative: " + id);
                }
                return epochMillis + (id >>> timeShift) * unitMillis;
            }
        };
    }

    /**
     * The date, the delimiter, then the inner text.
     */
    private abstract static class Prefixed implements IdCodec {
        private final DateText date;
        private final String delimiter;
        private final IdCodec inner;

        Prefixed(DateText date, String delimiter, IdCodec inner) {
            this.date = date;
            this.delimiter = delimiter;
            this.inner = inner;
        }

        abstract long epochMillis(long id);

        @Override
        public String encode(long id) {
            return date.format(epochMillis(id)) + delimiter + inner.encode(id);
        }

        @Override
        public long decode(String text) {
            int end = date.end(text);
            if (end < 0 || !text.startsWith(delimiter, end)) {
                throw new IllegalArgumentException("[" + text + "] does not start with a date and the delimiter [" + delimiter + "]");
            }
            return inner.decode(text.substring(end + delimiter.length()));
        }
    }
}
