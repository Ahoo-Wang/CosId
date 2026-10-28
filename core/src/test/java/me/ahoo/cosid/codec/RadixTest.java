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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Random;

class RadixTest {
    @Test
    void knownValues() {
        assertThat(Radix.of(62, 11, false).encode(Long.MAX_VALUE), is("AzL8n0Y58m7"));
        assertThat(Radix.of(36, 13, false).encode(Long.MAX_VALUE), is("1Y2P0IJ32E8E7"));
        assertThat(Radix.of(62, 11, true).encode(865_000_000_000_000_000L + 4096 + 7), is("11thy4jRXEd"));
        assertThat(Radix.of(62, 11, true).encode(0), is("00000000000"));
        assertThat(Radix.of(62, 11, false).encode(0), is("0"));
        assertThat(Radix.of(10, 6, true).encode(42), is("000042"));
        assertThat(Radix.of(62, 11, true).decode("AzL8n0Y58m7"), is(Long.MAX_VALUE));
    }

    @Test
    void roundTripsEveryRadixAndWidth() {
        Random random = new Random(7);
        for (int radix = Radix.MIN_RADIX; radix <= Radix.MAX_RADIX; radix++) {
            for (int width = 1; width <= Radix.maxWidth(radix); width++) {
                for (boolean pad : new boolean[] {true, false}) {
                    Radix codec = Radix.of(radix, width, pad);
                    long max = codec.maxValue();
                    long[] samples = {0, 1, max, max - 1, max / 2, Math.floorMod(random.nextLong(), max) , Math.floorMod(random.nextLong(), max)};
                    for (long id : samples) {
                        if (id < 0) {
                            continue;
                        }
                        String text = codec.encode(id);
                        assertThat(codec.decode(text), is(id));
                        if (pad) {
                            assertThat(text.length(), is(width));
                        } else {
                            assertThat(text.length(), lessThanOrEqualTo(width));
                            assertThat(text.length() == 1 || text.charAt(0) != '0', is(true));
                        }
                        if (radix <= 36) {
                            String expected = Long.toString(id, radix).toUpperCase(Locale.ROOT);
                            assertThat(text.replaceFirst("^0+(?=.)", ""), is(expected));
                        }
                    }
                    if (max < Long.MAX_VALUE) {
                        long beyond = max + 1;
                        assertThrows(IllegalArgumentException.class, () -> codec.encode(beyond));
                    }
                }
            }
        }
    }

    @Test
    void paddedTextSortsLikeTheIds() {
        Radix codec = Radix.of(62, 11, true);
        Random random = new Random(3);
        for (int i = 0; i < 10_000; i++) {
            long a = random.nextLong() >>> 1;
            long b = random.nextLong() >>> random.nextInt(1, 64);
            assertThat(Integer.signum(codec.encode(a).compareTo(codec.encode(b))), is(Long.signum(Long.compare(a, b))));
        }
    }

    @Test
    void rejectsInvalidInput() {
        Radix codec = Radix.of(62, 11, true);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("000000000000"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("abc-"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("abcé"));
        assertThrows(IllegalArgumentException.class, () -> Radix.of(36, 13, true).decode("abc"));
        assertThrows(IllegalArgumentException.class, () -> Radix.of(10, 3, true).decode("A"));
        // Eleven radix-62 digits hold more than a long.
        assertThrows(IllegalArgumentException.class, () -> codec.decode("AzL8n0Y58m8"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("zzzzzzzzzzz"));
        assertThrows(IllegalArgumentException.class, () -> Radix.of(2, 63, false).decode("1".repeat(63) + "1"));
        assertThat(Radix.of(2, 63, false).decode("1".repeat(63)), is(Long.MAX_VALUE));
    }

    @Test
    void constantRadixPathsMatchMaxWidth() {
        // readDigits passes these maxWidths as literals for its constant-radix cases.
        assertThat(Radix.maxWidth(62), is(11));
        assertThat(Radix.maxWidth(36), is(13));
        assertThat(Radix.maxWidth(10), is(19));
    }

    @Test
    void decodesTheBoundariesOfEachConstantRadix() {
        assertBoundaries(62, "AzL8n0Y58m7", "AzL8n0Y58m8");
        assertBoundaries(36, "1Y2P0IJ32E8E7", "1Y2P0IJ32E8E8");
        assertBoundaries(10, "9223372036854775807", "9223372036854775808");
        assertBoundaries(16, "7FFFFFFFFFFFFFFF", "8000000000000000");
    }

    private static void assertBoundaries(int radix, String max, String maxPlusOne) {
        int maxWidth = Radix.maxWidth(radix);
        Radix codec = Radix.of(radix, maxWidth, false);
        char top = (char) (radix <= 10 ? '0' + radix - 1 : radix <= 36 ? 'A' + radix - 11 : 'a' + radix - 37);
        assertThat(codec.decode(max), is(Long.MAX_VALUE));
        assertThat(codec.decode("0".repeat(maxWidth)), is(0L));
        // maxWidth - 1 digits never overflow, whatever they are.
        String belowWidth = String.valueOf(top).repeat(maxWidth - 1);
        assertThat(codec.decode(belowWidth), is(BigInteger.valueOf(radix).pow(maxWidth - 1).longValueExact() - 1));
        // The maxWidth-th digit is the one that overflows.
        IllegalArgumentException overflow = assertThrows(IllegalArgumentException.class, () -> codec.decode(maxPlusOne));
        assertThat(overflow.getMessage(), containsString("overflows a long"));
        assertThat(overflow.getCause(), instanceOf(ArithmeticException.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(belowWidth + top));
        // A character beyond the radix, in the unchecked digits and in the checked last digit.
        char beyond = radix == 62 ? '-' : (char) (radix < 10 ? '0' + radix : radix < 36 ? 'A' + radix - 10 : 'a' + radix - 36);
        IllegalArgumentException first = assertThrows(IllegalArgumentException.class, () -> codec.decode(beyond + max.substring(1)));
        assertThat(first.getMessage(), containsString("[" + beyond + "] that is not a digit in radix " + radix + " at 0"));
        String lastBeyond = max.substring(0, maxWidth - 1) + beyond;
        IllegalArgumentException last = assertThrows(IllegalArgumentException.class, () -> codec.decode(lastBeyond));
        assertThat(last.getMessage(), containsString("at " + (maxWidth - 1)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(max.substring(0, 3) + '\u00e9' + max.substring(4)));
        // Beyond maxWidth the length check fires first.
        assertThrows(IllegalArgumentException.class, () -> codec.decode("0" + max));
    }

    @Test
    void decodeMatchesBigIntegerForEveryRadix() {
        Random random = new Random(11);
        String digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        BigInteger longMax = BigInteger.valueOf(Long.MAX_VALUE);
        for (int radix = Radix.MIN_RADIX; radix <= Radix.MAX_RADIX; radix++) {
            int maxWidth = Radix.maxWidth(radix);
            Radix codec = Radix.of(radix, maxWidth, false);
            for (int i = 0; i < 2_000; i++) {
                int length = random.nextInt(1, maxWidth + 1);
                StringBuilder text = new StringBuilder(length);
                BigInteger expected = BigInteger.ZERO;
                for (int j = 0; j < length; j++) {
                    int digit = random.nextInt(radix);
                    text.append(digits.charAt(digit));
                    expected = expected.multiply(BigInteger.valueOf(radix)).add(BigInteger.valueOf(digit));
                }
                String value = text.toString();
                if (expected.compareTo(longMax) > 0) {
                    assertThrows(IllegalArgumentException.class, () -> codec.decode(value));
                } else {
                    assertThat(codec.decode(value), is(expected.longValueExact()));
                }
            }
        }
    }
}
