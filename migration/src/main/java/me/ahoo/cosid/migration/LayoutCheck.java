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

package me.ahoo.cosid.migration;

import me.ahoo.cosid.time.Layout;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The layout validation of one time-based generator (09 §4.3, M3).
 *
 * @param generator the 5.0 generator name
 * @param source    the 4.x layout
 * @param target    the 5.0 layout
 * @param ok        whether 5.0 ids cannot meet 4.x ids in one column
 * @param reason    why
 */
public record LayoutCheck(String generator, String source, String target, boolean ok, String reason) {
    /**
     * Checks a pair of layouts: the same layout and epoch pass (M2 then keeps them apart); otherwise the smallest 5.0 id
     * at W must exceed the largest 4.x id that W bounds, {@code new.pack(W, 0, 0) > old.pack(W, maxNode, maxSeq)}.
     *
     * @param generator the generator
     * @param old       the 4.x layout
     * @param neu       the 5.0 layout
     * @param w         W, epoch milliseconds
     * @return the check
     */
    public static LayoutCheck check(String generator, Layout old, Layout neu, long w) {
        if (old.equals(neu)) {
            return new LayoutCheck(generator, describe(old), describe(neu), true, "same layout and epoch: the first 5.0 timestamp is after W (M2)");
        }
        if (old.totalBits() > Layout.LONG_BITS || neu.totalBits() > Layout.LONG_BITS) {
            return new LayoutCheck(generator, describe(old), describe(neu), false,
                "an 80-bit layout differs: the ids cannot be compared; keep the 4.x layout or use a new column");
        }
        try {
            long oldMax = old.pack(time(old, w), (int) ~(-1L << old.nodeBits()), ~(-1L << old.seqBits()));
            long newMin = neu.pack(time(neu, w), 0, 0);
            boolean ok = newMin > oldMax;
            return new LayoutCheck(generator, describe(old), describe(neu), ok, ok
                ? "the smallest 5.0 id at W (" + newMin + ") exceeds the largest 4.x id up to W (" + oldMax + ")"
                : "5.0 ids from " + newMin + " can meet 4.x ids up to " + oldMax + ": keep the 4.x layout or use a new column");
        } catch (IllegalArgumentException e) {
            return new LayoutCheck(generator, describe(old), describe(neu), false, "W does not fit a layout: " + e.getMessage());
        }
    }

    private static long time(Layout layout, long w) {
        long since = w - layout.epoch().toEpochMilli();
        if (since < 0) {
            throw new IllegalArgumentException("W is before the epoch " + layout.epoch());
        }
        return since / layout.unit().getDuration().toMillis();
    }

    /**
     * A layout as text: {@code epoch=... unit=... bits=time/node/seq}.
     *
     * @param layout the layout
     * @return the text
     */
    public static String describe(Layout layout) {
        return "epoch=" + layout.epoch().toEpochMilli() + " unit=" + layout.unit() + " bits=" + layout.timeBits() + "/" + layout.nodeBits() + "/" + layout.seqBits();
    }

    /**
     * The layout of a 4.x snowflake id.
     *
     * @param epochMillis the epoch
     * @param seconds     whether the timestamp unit is seconds
     * @param timeBits    the timestamp bits
     * @param nodeBits    the machine bits
     * @param seqBits     the sequence bits
     * @return the layout
     */
    public static Layout snowflake(long epochMillis, boolean seconds, int timeBits, int nodeBits, int seqBits) {
        return new Layout(Instant.ofEpochMilli(epochMillis), seconds ? ChronoUnit.SECONDS : ChronoUnit.MILLIS, timeBits, nodeBits, seqBits,
            Layout.defaultSeqResetThreshold(seqBits));
    }
}
