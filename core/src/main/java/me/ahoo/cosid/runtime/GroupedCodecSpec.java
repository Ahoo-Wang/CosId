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
 * The codec of a {@link GeneratorSpec.PeriodicSegment} generator. In configuration the codec list starts with the
 * {@code date-prefix} item; the rest applies to the value.
 */
public sealed interface GroupedCodecSpec {
    /**
     * {@link GroupedCodec#datePrefix}.
     *
     * @param delimiter the text between the period and the value
     * @param value     the codec list of the value, applied in order
     */
    record DatePrefix(String delimiter, List<CodecSpec> value) implements GroupedCodecSpec {
        public DatePrefix {
            Objects.requireNonNull(delimiter, "delimiter");
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
