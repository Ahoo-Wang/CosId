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

/**
 * Encodes non-negative ids in base 2 to 62 with the digits {@code 0-9A-Za-z}; replaces the 4.x
 * {@code ToStringIdConverter}, {@code Radix36IdConverter} and {@code Radix62IdConverter} (03-core-model.md §1).
 */
public final class Radix implements IdCodec {
    public static final int MIN_RADIX = 2;
    public static final int MAX_RADIX = 62;

    private final int radix;
    private final int width;
    private final boolean padStart;
    private final long maxValue;

    private Radix(int radix, int width, boolean padStart) {
        this.radix = radix;
        this.width = width;
        this.padStart = padStart;
        this.maxValue = computeMaxValue(radix, width);
    }

    /**
     * A radix codec.
     *
     * @param radix    the base, from {@value #MIN_RADIX} to {@value #MAX_RADIX}
     * @param width    the most digits an id takes, from 1 to {@link #maxWidth(int)}
     * @param padStart whether shorter ids are padded with leading zeros to {@code width}
     * @return the codec
     */
    public static Radix of(int radix, int width, boolean padStart) {
        if (radix < MIN_RADIX || radix > MAX_RADIX) {
            throw new IllegalArgumentException("radix must be in [" + MIN_RADIX + ", " + MAX_RADIX + "]: " + radix);
        }
        if (width < 1 || width > maxWidth(radix)) {
            throw new IllegalArgumentException("width must be in [1, " + maxWidth(radix) + "] for radix " + radix + ": " + width);
        }
        return new Radix(radix, width, padStart);
    }

    /**
     * The digits {@code Long.MAX_VALUE} takes in {@code radix}: the widest a radix codec can be.
     *
     * @param radix the base
     * @return the number of digits
     */
    public static int maxWidth(int radix) {
        int width = 0;
        for (long rest = Long.MAX_VALUE; rest > 0; rest /= radix) {
            width++;
        }
        return width;
    }

    private static long computeMaxValue(int radix, int width) {
        long max = 1;
        for (int i = 0; i < width; i++) {
            if (max > Long.MAX_VALUE / radix) {
                return Long.MAX_VALUE;
            }
            max *= radix;
        }
        return max - 1;
    }

    public int radix() {
        return radix;
    }

    public int width() {
        return width;
    }

    public boolean padStart() {
        return padStart;
    }

    /**
     * The largest id that fits {@link #width()} digits, computed with integer arithmetic.
     *
     * @return the largest encodable id
     */
    public long maxValue() {
        return maxValue;
    }

    @Override
    public String encode(long id) {
        throw new UnsupportedOperationException("R1-A");
    }

    @Override
    public long decode(String text) {
        throw new UnsupportedOperationException("R1-A");
    }
}
