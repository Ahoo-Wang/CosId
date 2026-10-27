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


package me.ahoo.cosid.integrations.jackson;

import me.ahoo.cosid.time.Layout;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Writes a {@code long} / {@code Long} id as JSON text and reads it back (07-runtime-and-spring.md §5).
 *
 * <p>The text is either the codec of a runtime generator ({@link #generator()}, which needs a {@link CosIdJacksonModule}
 * bound to the runtime) or an inline {@link #codec()}:
 * <pre>{@code
 * @AsString long id;                                                    // radix 62, 11 digits, padded (as in 4.x)
 * @AsString(generator = "order") long orderId;                          // the codec of generator "order"
 * @AsString(codec = Codec.TO_STRING) long a;                            // decimal text
 * @AsString(radix = 36, width = 13, padStart = false) long b;
 * @AsString(codec = Codec.FRIENDLY, layout = Preset.SNOWFLAKE_S, zone = "Asia/Shanghai") long c;
 * @AsString(codec = Codec.FRIENDLY, timeBits = 41, nodeBits = 5, seqBits = 17) long d;
 * }</pre>
 * Reading also accepts a JSON number that the codec could have written, and reads an empty string as {@code null}.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@JacksonAnnotationsInside
@JsonSerialize(using = AsStringSerializer.class)
@JsonDeserialize(using = AsStringDeserializer.class)
public @interface AsString {
    /**
     * A generator of the bound runtime whose codec writes the text; set, the inline attributes must stay unset.
     *
     * @return the generator name, empty for the inline {@link #codec()}
     */
    String generator() default "";

    /**
     * The inline codec.
     *
     * @return the codec
     */
    Codec codec() default Codec.RADIX;

    /**
     * {@link Codec#RADIX}: the radix, 2 to 62.
     *
     * @return the radix
     */
    int radix() default 62;

    /**
     * {@link Codec#RADIX}: the digits; {@code 0} for as many as a {@code long} takes in the radix.
     *
     * @return the width
     */
    int width() default 0;

    /**
     * {@link Codec#RADIX} and {@link Codec#FRIENDLY}: pads to the full width with leading zeros.
     *
     * @return whether to pad
     */
    boolean padStart() default true;

    /**
     * {@link Codec#FRIENDLY}: the layout preset, or the base of {@link #timeBits()}, {@link #nodeBits()},
     * {@link #seqBits()} and {@link #epoch()} when they are set.
     *
     * @return the preset
     */
    Preset layout() default Preset.SNOWFLAKE_MS;

    /**
     * {@link Codec#FRIENDLY}: the time bits; with {@link #nodeBits()} and {@link #seqBits()}, all three or none.
     *
     * @return the bits, {@code 0} for the preset's
     */
    int timeBits() default 0;

    /**
     * {@link Codec#FRIENDLY}: the node bits.
     *
     * @return the bits, {@code -1} for the preset's
     */
    int nodeBits() default -1;

    /**
     * {@link Codec#FRIENDLY}: the sequence bits.
     *
     * @return the bits, {@code 0} for the preset's
     */
    int seqBits() default 0;

    /**
     * {@link Codec#FRIENDLY}: the epoch in milliseconds.
     *
     * @return the epoch, {@code -1} for the preset's
     */
    long epoch() default -1;

    /**
     * {@link Codec#FRIENDLY}: the time zone of the date and time.
     *
     * @return the zone id, empty for the system default
     */
    String zone() default "";

    /** The inline codecs (the 4.x {@code AsString.Type}). */
    enum Codec {
        /** Decimal text. */
        TO_STRING,
        /** {@link me.ahoo.cosid.codec.Radix}. */
        RADIX,
        /** {@link me.ahoo.cosid.codec.Friendly}: {@code yyyyMMddHHmmssSSS-node-seq}. */
        FRIENDLY
    }

    /** The {@link Layout} presets of a friendly id. */
    enum Preset {
        /** {@link Layout#SNOWFLAKE_MS}. */
        SNOWFLAKE_MS(Layout.SNOWFLAKE_MS),
        /** {@link Layout#SNOWFLAKE_S}. */
        SNOWFLAKE_S(Layout.SNOWFLAKE_S),
        /** {@link Layout#JS_SAFE_MS}. */
        JS_SAFE_MS(Layout.JS_SAFE_MS),
        /** {@link Layout#JS_SAFE_S}. */
        JS_SAFE_S(Layout.JS_SAFE_S);

        private final Layout layout;

        Preset(Layout layout) {
            this.layout = layout;
        }

        /**
         * The layout.
         *
         * @return the layout
         */
        public Layout layout() {
            return layout;
        }
    }
}
