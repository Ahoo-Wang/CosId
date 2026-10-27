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

import java.util.Objects;

/**
 * Turns a {@link GroupedId} into text and back; grouped ids have their own codec, apart from {@link IdCodec}
 * (04-segment.md §3).
 */
public interface GroupedCodec {
    String encode(GroupedId id);

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
}
