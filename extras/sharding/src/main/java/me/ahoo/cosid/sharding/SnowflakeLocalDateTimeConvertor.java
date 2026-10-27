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

import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.time.Layout;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Converts a Snowflake id, a {@code Long} or its text, to the time it was issued: {@link Layout#unpack} gives the
 * time part (D7; 4.x used a {@code SnowflakeIdStateParser}).
 */
public class SnowflakeLocalDateTimeConvertor implements LocalDateTimeConvertor {
    /**
     * The text form of 4.x's {@code Radix62IdConverter.PAD_START}: base 62, 11 digits, padded.
     */
    public static final IdCodec RADIX62_PAD_START = Radix.of(62, 11, true);

    private final Layout layout;
    private final ZoneId zoneId;
    private final IdCodec codec;

    /**
     * Creates the convertor; text values are base 62, 11 digits, padded.
     *
     * @param layout the layout of the ids
     * @param zoneId the zone of the local date-time
     */
    public SnowflakeLocalDateTimeConvertor(Layout layout, ZoneId zoneId) {
        this(layout, zoneId, RADIX62_PAD_START);
    }

    /**
     * Creates the convertor.
     *
     * @param layout the layout of the ids
     * @param zoneId the zone of the local date-time
     * @param codec  decodes text values
     */
    public SnowflakeLocalDateTimeConvertor(Layout layout, ZoneId zoneId, IdCodec codec) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public LocalDateTime toLocalDateTime(Comparable<?> value) {
        return LocalDateTime.ofInstant(layout.unpack(toId(value)).instant(layout), zoneId);
    }

    private long toId(Comparable<?> value) {
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof String text) {
            return codec.decode(text);
        }
        throw new IllegalArgumentException("Unsupported sharding value type `" + value + "`.");
    }
}
