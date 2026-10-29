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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.codec.Friendly;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.codec.Radix;
import me.ahoo.cosid.runtime.CodecSpec;
import me.ahoo.cosid.runtime.GroupedCodecSpec;
import me.ahoo.cosid.runtime.PartsCodecSpec;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the codecs of a runtime from their specs (07-runtime-and-spring.md §4.1): a codec list applies in order, its
 * first item turns the id into text and the rest decorate the text.
 */
public final class Codecs {
    /** The codec of an empty list: the decimal digits, as {@code Long.toString}. */
    public static final IdCodec DECIMAL = Radix.of(10, Radix.maxWidth(10), false);

    private final TimeSource time;
    private final Function<String, Optional<IdCodec>> custom;

    /**
     * Codecs built against one time source and custom codec resolver.
     *
     * @param time   the time source of {@code NOW} date prefixes
     * @param custom resolves custom codec references
     */
    public Codecs(TimeSource time, Function<String, Optional<IdCodec>> custom) {
        this.time = time;
        this.custom = custom;
    }

    /**
     * The codec of a {@code long} generator.
     *
     * @param specs  the codec list
     * @param layout the generator's layout; empty for segments
     * @return the codec
     * @throws IllegalArgumentException if the list is invalid for the generator
     */
    public IdCodec ids(List<CodecSpec> specs, Optional<Layout> layout) {
        return apply(DECIMAL, specs, true, layout);
    }

    /**
     * Decorates the text of a codec with a list of decorations (prefixes, suffixes, date prefixes).
     *
     * @param codec  the codec whose text the list decorates
     * @param specs  the decorations, applied in order
     * @param layout the generator's layout; empty for segments
     * @return the decorated codec
     * @throws IllegalArgumentException if an item turns the id into text instead of decorating it
     */
    public IdCodec decorate(IdCodec codec, List<CodecSpec> specs, Optional<Layout> layout) {
        return apply(codec, specs, false, layout);
    }

    private IdCodec apply(IdCodec start, List<CodecSpec> specs, boolean fromId, Optional<Layout> layout) {
        IdCodec codec = start;
        for (int i = 0; i < specs.size(); i++) {
            CodecSpec spec = specs.get(i);
            boolean first = fromId && i == 0;
            codec = switch (spec) {
                case CodecSpec.Radix radix -> {
                    requireFirst(first, spec);
                    yield Radix.of(radix.radix(), radix.width(), radix.padStart());
                }
                case CodecSpec.Friendly friendly -> {
                    requireFirst(first, spec);
                    Layout idLayout = layout.orElseThrow(() -> new IllegalArgumentException("A friendly codec needs a time-based generator"));
                    yield Friendly.of(idLayout, friendly.zone(), friendly.padStart());
                }
                case CodecSpec.Custom ref -> {
                    requireFirst(first, spec);
                    yield custom.apply(ref.reference())
                        .orElseThrow(() -> new IllegalArgumentException("No codec [" + ref.reference() + "]"));
                }
                case CodecSpec.Instance instance -> {
                    requireFirst(first, spec);
                    yield instance.codec();
                }
                case CodecSpec.Prefix prefix -> codec.withPrefix(prefix.prefix());
                case CodecSpec.Suffix suffix -> codec.withSuffix(suffix.suffix());
                case CodecSpec.DatePrefix date -> {
                    if (date.source() == me.ahoo.cosid.codec.DatePrefix.Source.ID_TIME && layout.isEmpty()) {
                        throw new IllegalArgumentException("A date prefix from the id time needs a time-based generator");
                    }
                    yield me.ahoo.cosid.codec.DatePrefix.of(date.pattern(), date.delimiter(), date.source())
                        .apply(codec, date.zone(), time, layout);
                }
            };
        }
        return codec;
    }

    private static void requireFirst(boolean first, CodecSpec spec) {
        if (!first) {
            throw new IllegalArgumentException(spec + " turns the id into text, so it must be the first codec");
        }
    }

    /**
     * The codec of a cosid80 generator.
     *
     * @param spec   the spec
     * @param layout the generator's layout
     * @return the parts codec
     */
    public static PartsCodec parts(PartsCodecSpec spec, Layout layout) {
        return switch (spec) {
            case PartsCodecSpec.Radix radix -> PartsCodec.radix(layout, radix.radix());
            case PartsCodecSpec.Friendly friendly -> PartsCodec.friendly(layout, friendly.zone(), friendly.padStart());
            case PartsCodecSpec.Instance instance -> instance.codec();
        };
    }

    /**
     * The codec of a periodic segment generator.
     *
     * @param spec   the spec
     * @param period the generator's period, which gives the length of the key
     * @return the grouped codec
     */
    public GroupedCodec grouped(GroupedCodecSpec spec, Period period) {
        return switch (spec) {
            case GroupedCodecSpec.DatePrefix date when date.outer().isEmpty() ->
                GroupedCodec.datePrefix(period, date.delimiter(), ids(date.value(), Optional.empty()));
            case GroupedCodecSpec.DatePrefix date -> {
                // Fails now rather than on the first id: the decorations compose once per period key.
                decorate(DECIMAL, date.outer(), Optional.empty());
                yield GroupedCodec.datePrefix(period, date.delimiter(), ids(date.value(), Optional.empty()),
                    codec -> decorate(codec, date.outer(), Optional.empty()));
            }
            case GroupedCodecSpec.ValueOnly value -> GroupedCodec.valueOnly(ids(value.value(), Optional.empty()));
            case GroupedCodecSpec.Instance instance -> instance.codec();
        };
    }
}
