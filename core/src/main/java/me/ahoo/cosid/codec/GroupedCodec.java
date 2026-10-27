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
     * @param delimiter  the text between the period and the value
     * @param valueCodec the codec of the value
     * @return the codec
     */
    static GroupedCodec datePrefix(String delimiter, IdCodec valueCodec) {
        throw new UnsupportedOperationException("R1-B");
    }
}
