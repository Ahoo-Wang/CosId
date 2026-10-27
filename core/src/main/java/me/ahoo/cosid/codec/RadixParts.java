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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * {@link PartsCodec#radix}: each part at the fixed width its bits need, so the text sorts like the id.
 */
final class RadixParts implements PartsCodec {
    private final Layout layout;
    private final int radix;
    private final int timeWidth;
    private final int nodeWidth;
    private final int seqWidth;
    private final long maxTime;
    private final long maxNode;
    private final long maxSeq;

    RadixParts(Layout layout, int radix) {
        this.layout = Objects.requireNonNull(layout, "layout");
        if (radix < Radix.MIN_RADIX || radix > Radix.MAX_RADIX) {
            throw new IllegalArgumentException("radix must be in [" + Radix.MIN_RADIX + ", " + Radix.MAX_RADIX + "]: " + radix);
        }
        this.radix = radix;
        this.timeWidth = Radix.partWidth(radix, layout.timeBits());
        this.nodeWidth = Radix.partWidth(radix, layout.nodeBits());
        this.seqWidth = Radix.partWidth(radix, layout.seqBits());
        this.maxTime = ~(-1L << layout.timeBits());
        this.maxNode = ~(-1L << layout.nodeBits());
        this.maxSeq = ~(-1L << layout.seqBits());
    }

    @Override
    public String encode(IdParts parts) {
        long time = parts.time();
        long node = parts.node();
        long seq = parts.seq();
        if (time < 0 || time > maxTime || node < 0 || node > maxNode || seq < 0 || seq > maxSeq) {
            throw new IllegalArgumentException(parts + " does not fit layout " + layout);
        }
        byte[] buf = new byte[timeWidth + nodeWidth + seqWidth];
        Arrays.fill(buf, (byte) '0');
        int end = timeWidth;
        write(buf, end, timeWidth, time);
        end += nodeWidth;
        write(buf, end, nodeWidth, node);
        end += seqWidth;
        write(buf, end, seqWidth, seq);
        return new String(buf, StandardCharsets.ISO_8859_1);
    }

    private void write(byte[] buf, int end, int width, long value) {
        if (width > 0) {
            Radix.writeDigits(buf, end, radix, value);
        }
    }

    @Override
    public IdParts decode(String text) {
        if (text.length() != timeWidth + nodeWidth + seqWidth) {
            throw new IllegalArgumentException("[" + text + "] must have " + (timeWidth + nodeWidth + seqWidth) + " digits in radix " + radix);
        }
        long time = Radix.readDigits(text, 0, timeWidth, radix);
        long node = Radix.readDigits(text, timeWidth, timeWidth + nodeWidth, radix);
        long seq = Radix.readDigits(text, timeWidth + nodeWidth, text.length(), radix);
        if (time > maxTime || node > maxNode || seq > maxSeq) {
            throw new IllegalArgumentException("[" + text + "] has a part beyond layout " + layout);
        }
        return new IdParts(time, (int) node, seq);
    }
}
