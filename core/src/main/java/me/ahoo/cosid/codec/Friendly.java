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
import java.util.Objects;

/**
 * Encodes a time-based id as readable text, {@code yyyyMMddHHmmssSSS-<node>-<seq>}, from {@link Layout#unpack(long)};
 * the time zone is always explicit (03-core-model.md §1). Formats through precomputed field widths into a
 * {@code char[]}, without {@code DateTimeFormatter}.
 */
public final class Friendly implements IdCodec {
    private final Layout layout;
    private final ZoneId zone;
    private final boolean padStart;
    private final FriendlyFormat format;
    private final int seqBits;
    private final int timeShift;
    private final long nodeMask;
    private final long seqMask;
    private final int totalBits;

    private Friendly(Layout layout, ZoneId zone, boolean padStart) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.padStart = padStart;
        if (layout.totalBits() > Layout.LONG_BITS) {
            throw new IllegalArgumentException("A " + layout.totalBits() + "-bit layout does not fit a long: use PartsCodec.friendly");
        }
        this.format = new FriendlyFormat(layout, zone, padStart);
        this.seqBits = layout.seqBits();
        this.timeShift = layout.nodeBits() + seqBits;
        this.nodeMask = ~(-1L << layout.nodeBits());
        this.seqMask = ~(-1L << seqBits);
        this.totalBits = layout.totalBits();
    }

    /**
     * A friendly codec.
     *
     * @param layout   the layout of the ids
     * @param zone     the time zone of the time part
     * @param padStart whether the node and sequence parts are padded with leading zeros
     * @return the codec
     * @throws IllegalArgumentException if the layout is wider than 63 bits: use {@link PartsCodec#friendly}
     */
    public static Friendly of(Layout layout, ZoneId zone, boolean padStart) {
        return new Friendly(layout, zone, padStart);
    }

    public Layout layout() {
        return layout;
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean padStart() {
        return padStart;
    }

    @Override
    public String encode(long id) {
        if (id < 0 || id >>> totalBits != 0) {
            throw new IllegalArgumentException("id " + id + " is not a " + totalBits + "-bit id");
        }
        return format.format(id >>> timeShift, id >>> seqBits & nodeMask, id & seqMask);
    }

    @Override
    public long decode(String text) {
        IdParts parts = format.parse(text);
        return layout.pack(parts.time(), parts.node(), parts.seq());
    }
}
