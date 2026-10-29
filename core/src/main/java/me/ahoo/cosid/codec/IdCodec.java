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

import java.util.Objects;

/**
 * Turns a {@code long} id into text and back (03-core-model.md §1).
 *
 * <p>Codecs compose: the codec list of a generator applies in the order it is written, the first item turning the id
 * into text and the rest decorating that text, such as {@link #withPrefix(String)}.
 */
public interface IdCodec {
    /**
     * Encodes an id (R1-core-16).
     *
     * @param id the id
     * @return the text, never null
     * @throws IllegalArgumentException if the codec cannot encode {@code id}: one outside its range, such as a negative
     *                                  id, or one wider than the digits of a fixed-width radix; a codec never truncates
     */
    String encode(long id);

    /**
     * Decodes text produced by {@link #encode(long)}.
     *
     * @param text the text
     * @return the id
     * @throws IllegalArgumentException if the text is not an encoded id
     */
    long decode(String text);

    /**
     * Prepends {@code prefix} to the encoded text.
     *
     * @param prefix the prefix
     * @return the decorated codec
     */
    default IdCodec withPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        IdCodec inner = this;
        return new IdCodec() {
            @Override
            public String encode(long id) {
                return prefix + inner.encode(id);
            }

            @Override
            public long decode(String text) {
                if (!text.startsWith(prefix)) {
                    throw new IllegalArgumentException("[" + text + "] does not start with [" + prefix + "]");
                }
                return inner.decode(text.substring(prefix.length()));
            }
        };
    }

    /**
     * Appends {@code suffix} to the encoded text.
     *
     * @param suffix the suffix
     * @return the decorated codec
     */
    default IdCodec withSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        IdCodec inner = this;
        return new IdCodec() {
            @Override
            public String encode(long id) {
                return inner.encode(id) + suffix;
            }

            @Override
            public long decode(String text) {
                if (!text.endsWith(suffix)) {
                    throw new IllegalArgumentException("[" + text + "] does not end with [" + suffix + "]");
                }
                return inner.decode(text.substring(0, text.length() - suffix.length()));
            }
        };
    }
}
