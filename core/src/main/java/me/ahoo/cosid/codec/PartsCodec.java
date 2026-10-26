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

import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;

import java.time.ZoneId;

/**
 * Encodes the parts of a time-based id as text; the only output of ids wider than 63 bits, such as
 * {@link Layout#COSID_80} (D4). Replaces the 4.x {@code Radix62CosIdGenerator}, {@code Radix36CosIdGenerator},
 * {@code FriendlyCosIdGenerator} and their state parsers.
 */
public interface PartsCodec {
    String encode(IdParts parts);

    IdParts decode(String text);

    /**
     * Each part in {@code radix} at the fixed width its bits need, so the text sorts like the id.
     *
     * @param layout the layout of the parts
     * @param radix  the base, from 2 to 62
     * @return the codec
     */
    static PartsCodec radix(Layout layout, int radix) {
        throw new UnsupportedOperationException("R1-A");
    }

    /**
     * The time part as {@code yyyyMMddHHmmssSSS} in {@code zone}, then the node and the sequence.
     *
     * @param layout the layout of the parts
     * @param zone   the time zone of the time part
     * @return the codec
     */
    static PartsCodec friendly(Layout layout, ZoneId zone) {
        throw new UnsupportedOperationException("R1-A");
    }
}
