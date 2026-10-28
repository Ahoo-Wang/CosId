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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Encodes non-negative ids in base 2 to 62 with the digits {@code 0-9A-Za-z}; replaces the 4.x
 * {@code ToStringIdConverter}, {@code Radix36IdConverter} and {@code Radix62IdConverter} (03-core-model.md §1).
 */
public final class Radix implements IdCodec {
    public static final int MIN_RADIX = 2;
    public static final int MAX_RADIX = 62;

    private static final byte[] DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] VALUES = new byte['z' + 1];

    static {
        Arrays.fill(VALUES, (byte) -1);
        for (int i = 0; i < DIGITS.length; i++) {
            VALUES[DIGITS[i]] = (byte) i;
        }
    }

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
        if (id < 0 || id > maxValue) {
            throw new IllegalArgumentException("id " + id + " must be in [0, " + maxValue + "] for " + width + " digits in radix " + radix);
        }
        byte[] buf = new byte[width];
        int start = writeDigits(buf, width, radix, id);
        if (padStart) {
            while (start > 0) {
                buf[--start] = '0';
            }
        }
        return new String(buf, start, width - start, StandardCharsets.ISO_8859_1);
    }

    @Override
    public long decode(String text) {
        int length = text.length();
        if (length == 0 || length > width) {
            throw new IllegalArgumentException("[" + text + "] must have 1 to " + width + " digits in radix " + radix);
        }
        return readDigits(text, 0, length, radix);
    }

    /**
     * Writes the digits of {@code value} in {@code radix} ending before {@code end}, most significant first.
     *
     * @return the index of the first digit written; one digit for 0
     */
    static int writeDigits(byte[] buf, int end, int radix, long value) {
        // A constant radix per case: once inlined, each division becomes a multiplication.
        return switch (radix) {
            case 62 -> write(buf, end, 62, value);
            case 36 -> write(buf, end, 36, value);
            case 10 -> write(buf, end, 10, value);
            default -> write(buf, end, radix, value);
        };
    }

    private static int write(byte[] buf, int end, int radix, long value) {
        int at = end;
        if (value == 0) {
            buf[--at] = '0';
            return at;
        }
        // The value > 0 guard bounds value % radix to [0, radix), so the JIT drops the DIGITS range check.
        while (value > 0) {
            buf[--at] = DIGITS[(int) (value % radix)];
            value /= radix;
        }
        return at;
    }

    /**
     * Reads the digits of {@code text} from {@code from} to {@code to} in {@code radix}.
     *
     * @throws IllegalArgumentException for a character that is not a digit of the radix, or a value beyond a long
     */
    static long readDigits(String text, int from, int to, int radix) {
        // A constant radix and its maxWidth per case, as in writeDigits.
        return switch (radix) {
            case 62 -> read(text, from, to, 62, 11);
            case 36 -> read(text, from, to, 36, 13);
            case 10 -> read(text, from, to, 10, 19);
            default -> read(text, from, to, radix, maxWidth(radix));
        };
    }

    private static long read(String text, int from, int to, int radix, int maxWidth) {
        // Fewer than maxWidth digits are below radix^(maxWidth - 1) <= Long.MAX_VALUE: only the digits from the
        // maxWidth-th on can overflow, so only those take the checked arithmetic.
        int unchecked = Math.min(to, from + maxWidth - 1);
        long value = 0;
        int i = from;
        for (; i < unchecked; i++) {
            value = value * radix + digit(text, i, radix);
        }
        for (; i < to; i++) {
            int digit = digit(text, i, radix);
            try {
                value = Math.addExact(Math.multiplyExact(value, radix), digit);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("[" + text + "] overflows a long in radix " + radix, overflow);
            }
        }
        return value;
    }

    private static int digit(String text, int at, int radix) {
        char c = text.charAt(at);
        int digit = c < VALUES.length ? VALUES[c] : -1;
        if (digit < 0 || digit >= radix) {
            throw invalidDigit(text, at, radix);
        }
        return digit;
    }

    private static IllegalArgumentException invalidDigit(String text, int at, int radix) {
        return new IllegalArgumentException("[" + text + "] has a character [" + text.charAt(at) + "] that is not a digit in radix " + radix + " at " + at);
    }

    /**
     * The digits of {@code 2^bits - 1} in {@code radix}: the fixed width of a part of {@code bits}.
     *
     * @return the width, 0 for 0 bits
     */
    static int partWidth(int radix, int bits) {
        int width = 0;
        for (long rest = ~(-1L << bits); rest > 0; rest /= radix) {
            width++;
        }
        return width;
    }
}
