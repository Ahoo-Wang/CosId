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

package me.ahoo.cosid.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The bit layout of time-based ids, {@code time | node | seq} from the high bits down: the one place of the bit
 * arithmetic (03-core-model.md §2).
 *
 * <p>A layout wider than 63 bits, such as {@link #COSID_80}, cannot pack a {@code long}: its ids are issued as
 * {@link IdParts} and encoded through a {@code PartsCodec}.
 *
 * @param epoch             the time origin; whole seconds for {@link ChronoUnit#SECONDS}
 * @param unit              the time unit, {@link ChronoUnit#MILLIS} or {@link ChronoUnit#SECONDS}
 * @param timeBits          the bits of the time part, 1 to 63
 * @param nodeBits          the bits of the node part, 0 to 31
 * @param seqBits           the bits of the sequence part, 1 to 62
 * @param seqResetThreshold the highest sequence a new time unit may start from (the low-bit scattering of 4.x)
 */
public record Layout(Instant epoch, ChronoUnit unit, int timeBits, int nodeBits, int seqBits, long seqResetThreshold) {
    /** The most bits a {@code long} id may take. */
    public static final int LONG_BITS = 63;
    /** The most bits a JavaScript number holds exactly. */
    public static final int JS_SAFE_BITS = 53;
    /** The 4.x epoch, 2019-12-24T16:00:00Z (D16). */
    public static final Instant COSID_EPOCH = Instant.ofEpochMilli(1577203200000L);

    /** Milliseconds, 41/10/12 bits from {@link #COSID_EPOCH}: the 4.x default (D16). */
    public static final Layout SNOWFLAKE_MS = new Layout(COSID_EPOCH, ChronoUnit.MILLIS, 41, 10, 12, defaultSeqResetThreshold(12));
    /** Seconds, 31/10/22 bits from {@link #COSID_EPOCH}. */
    public static final Layout SNOWFLAKE_S = new Layout(COSID_EPOCH, ChronoUnit.SECONDS, 31, 10, 22, defaultSeqResetThreshold(22));
    /** Milliseconds, 41/3/9 bits: 53 bits, safe in JavaScript. */
    public static final Layout JS_SAFE_MS = new Layout(COSID_EPOCH, ChronoUnit.MILLIS, 41, 3, 9, defaultSeqResetThreshold(9));
    /** Seconds, 31/3/19 bits: 53 bits, safe in JavaScript. */
    public static final Layout JS_SAFE_S = new Layout(COSID_EPOCH, ChronoUnit.SECONDS, 31, 3, 19, defaultSeqResetThreshold(19));
    /** The 80-bit CosId of 4.x: milliseconds since the Unix epoch, 44/20/16 bits; parts only (D4). */
    public static final Layout COSID_80 = new Layout(Instant.EPOCH, ChronoUnit.MILLIS, 44, 20, 16, defaultSeqResetThreshold(16));

    public Layout {
        Objects.requireNonNull(epoch, "epoch");
        Objects.requireNonNull(unit, "unit");
        if (unit != ChronoUnit.MILLIS && unit != ChronoUnit.SECONDS) {
            throw new IllegalArgumentException("unit must be MILLIS or SECONDS: " + unit);
        }
        if (unit == ChronoUnit.SECONDS && epoch.getNano() != 0) {
            throw new IllegalArgumentException("epoch must be whole seconds for unit SECONDS: " + epoch);
        }
        checkRange("timeBits", timeBits, 1, LONG_BITS);
        checkRange("nodeBits", nodeBits, 0, Integer.SIZE - 1);
        checkRange("seqBits", seqBits, 1, LONG_BITS - 1);
        long maxSeq = ~(-1L << seqBits);
        if (seqResetThreshold < 0 || seqResetThreshold > maxSeq) {
            throw new IllegalArgumentException("seqResetThreshold must be in [0, " + maxSeq + "]: " + seqResetThreshold);
        }
    }

    private static void checkRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]: " + value);
        }
    }

    /**
     * The 4.x default reset threshold: half the sequence range.
     *
     * @param seqBits the bits of the sequence part
     * @return {@code 2^(seqBits-1) - 1}
     */
    public static long defaultSeqResetThreshold(int seqBits) {
        return ~(-1L << (seqBits - 1));
    }

    public int totalBits() {
        return timeBits + nodeBits + seqBits;
    }

    /**
     * Whether every id of this layout is exactly representable in JavaScript.
     *
     * @return whether {@link #totalBits()} is at most {@value #JS_SAFE_BITS}
     */
    public boolean isJsSafe() {
        return totalBits() <= JS_SAFE_BITS;
    }

    /**
     * Whether {@code id} is exactly representable in JavaScript.
     *
     * @param id the id
     * @return whether {@code 0 <= id < 2^53}
     */
    public static boolean isJsSafe(long id) {
        return id >= 0 && id < (1L << JS_SAFE_BITS);
    }

    /**
     * This layout with another node part; the time and sequence parts stay as they are.
     *
     * @param nodeBits the bits of the node part
     * @return the layout
     * @throws IllegalArgumentException if a layout that packed a {@code long} no longer would
     */
    public Layout withNodeBits(int nodeBits) {
        Layout layout = new Layout(epoch, unit, timeBits, nodeBits, seqBits, seqResetThreshold);
        if (totalBits() <= LONG_BITS && layout.totalBits() > LONG_BITS) {
            throw new IllegalArgumentException("nodeBits " + nodeBits + " takes the layout past " + LONG_BITS + " bits");
        }
        return layout;
    }

    /**
     * Packs the parts into an id.
     *
     * @param time the time, in units since the epoch
     * @param node the node
     * @param seq  the sequence
     * @return the id
     * @throws IllegalStateException if the layout is wider than {@value #LONG_BITS} bits
     */
    public long pack(long time, int node, long seq) {
        throw new UnsupportedOperationException("R1-A");
    }

    public IdParts unpack(long id) {
        throw new UnsupportedOperationException("R1-A");
    }
}
