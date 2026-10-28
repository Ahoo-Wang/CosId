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

package me.ahoo.cosid.runtime;

import me.ahoo.cosid.codec.GroupedCodec;

import java.util.List;
import java.util.Objects;

/**
 * The codec of a {@link GeneratorSpec.PeriodicSegment} generator. In configuration the codec list holds the period
 * item where the period key goes (or starts with the {@code date-prefix} item, the period first); the items before it
 * apply to the value, the items after it decorate the text that starts with the period key.
 *
 * <p>The variants are the current snapshot of a closed set of specs: a minor version may add a variant, so keep a
 * {@code default} branch in a {@code switch} over them (D40).
 */
public sealed interface GroupedCodecSpec {
    /**
     * {@link GroupedCodec#datePrefix(me.ahoo.cosid.segment.Period, String, me.ahoo.cosid.codec.IdCodec,
     * java.util.function.UnaryOperator)}: the period key, the delimiter, then the value; {@code outer} decorates the
     * whole, so with a prefix in it the period follows a business prefix (the 4.x {@code group-prefix.before-prefix}).
     *
     * @param delimiter the text between the period and the value
     * @param value     the codec list of the value, applied in order
     * @param outer     the decorations of the text that starts with the period key (prefixes, suffixes, date prefixes),
     *                  applied in order; empty puts the period first
     */
    record DatePrefix(String delimiter, List<CodecSpec> value, List<CodecSpec> outer) implements GroupedCodecSpec {
        public DatePrefix {
            Objects.requireNonNull(delimiter, "delimiter");
            value = List.copyOf(Objects.requireNonNull(value, "value"));
            outer = List.copyOf(Objects.requireNonNull(outer, "outer"));
        }

        /**
         * The period first: the period key, the delimiter, then the value.
         *
         * @param delimiter the text between the period and the value
         * @param value     the codec list of the value, applied in order
         */
        public DatePrefix(String delimiter, List<CodecSpec> value) {
            this(delimiter, value, List.of());
        }
    }

    /**
     * {@link GroupedCodec#valueOnly}: the text holds the value alone, without its period (the 4.x
     * {@code group-prefix.enabled = false}); such text cannot be decoded.
     *
     * @param value the codec list of the value, applied in order
     */
    record ValueOnly(List<CodecSpec> value) implements GroupedCodecSpec {
        public ValueOnly {
            value = List.copyOf(Objects.requireNonNull(value, "value"));
        }
    }

    /**
     * A codec instance, for specs built in code.
     *
     * @param codec the codec
     */
    record Instance(GroupedCodec codec) implements GroupedCodecSpec {
        public Instance {
            Objects.requireNonNull(codec, "codec");
        }
    }
}
