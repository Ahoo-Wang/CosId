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

package me.ahoo.cosid.store.zookeeper;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * The byte level of the node formats: ASCII written and read in place, with no intermediate strings for the names,
 * numbers and incarnations, since renew and reserve encode and decode a node on every call (D21, allocation). Strings
 * are URL-encoded exactly as {@link URLEncoder} does, so every node stays readable text.
 */
final class Ascii {
    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private Ascii() {
    }

    static byte[] bytes(String ascii) {
        return ascii.getBytes(StandardCharsets.US_ASCII);
    }

    /** Whether {@code [from, to)} of {@code data} equals {@code name}. */
    static boolean is(byte[] data, int from, int to, byte[] name) {
        return Arrays.equals(data, from, to, name, 0, name.length);
    }

    /**
     * A decimal long followed by {@code suffix}, in one array of the exact length (R1-zk-01: no buffer to copy).
     */
    static byte[] number(long value, byte[] suffix) {
        if (value == Long.MIN_VALUE) {
            return new Writer(32).number(value).raw(suffix).toBytes();
        }
        boolean negative = value < 0;
        long rest = negative ? -value : value;
        int digits = 1;
        for (long left = rest / 10; left > 0; left /= 10) {
            digits++;
        }
        int sign = negative ? 1 : 0;
        byte[] data = new byte[sign + digits + suffix.length];
        if (negative) {
            data[0] = '-';
        }
        for (int i = sign + digits - 1; i >= sign; i--) {
            data[i] = (byte) ('0' + rest % 10);
            rest /= 10;
        }
        System.arraycopy(suffix, 0, data, sign + digits, suffix.length);
        return data;
    }

    /** Parses a decimal long in {@code [from, to)}, with an optional leading minus. */
    static long number(byte[] data, int from, int to) {
        if (from >= to) {
            throw new NumberFormatException("empty number");
        }
        boolean negative = data[from] == '-';
        int i = negative ? from + 1 : from;
        if (i >= to) {
            throw new NumberFormatException("no digits");
        }
        long value = 0;
        for (; i < to; i++) {
            int digit = data[i] - '0';
            if (digit < 0 || digit > 9) {
                throw new NumberFormatException("not a digit: " + (char) data[i]);
            }
            // Accumulated negative, so Long.MIN_VALUE parses too.
            value = Math.subtractExact(Math.multiplyExact(value, 10), digit);
        }
        if (negative) {
            return value;
        }
        if (value == Long.MIN_VALUE) {
            throw new NumberFormatException("out of range");
        }
        return -value;
    }

    /** Parses the 36-character text form of a UUID in {@code [from, to)}. */
    static UUID uuid(byte[] data, int from, int to) {
        if (to - from != 36 || data[from + 8] != '-' || data[from + 13] != '-' || data[from + 18] != '-' || data[from + 23] != '-') {
            throw new IllegalArgumentException("not a UUID");
        }
        long msb = hex(data, from, from + 8) << 32 | hex(data, from + 9, from + 13) << 16 | hex(data, from + 14, from + 18);
        long lsb = hex(data, from + 19, from + 23) << 48 | hex(data, from + 24, from + 36);
        return new UUID(msb, lsb);
    }

    private static long hex(byte[] data, int from, int to) {
        long value = 0;
        for (int i = from; i < to; i++) {
            int digit = Character.digit(data[i], 16);
            if (digit < 0) {
                throw new IllegalArgumentException("not a hex digit: " + (char) data[i]);
            }
            value = value << 4 | digit;
        }
        return value;
    }

    /** A URL-decoded string in {@code [from, to)}. */
    static String text(byte[] data, int from, int to) {
        String raw = new String(data, from, to - from, StandardCharsets.ISO_8859_1);
        return raw.indexOf('%') < 0 && raw.indexOf('+') < 0 ? raw : URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** Whether {@link URLEncoder} keeps the character as it is. */
    private static boolean plain(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '*' || c == '_';
    }

    /** Builds node data. */
    static final class Writer {
        private byte[] buffer;
        private int length;

        Writer(int capacity) {
            buffer = new byte[capacity];
        }

        private void ensure(int more) {
            if (length + more > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, length + more));
            }
        }

        Writer raw(byte[] bytes) {
            ensure(bytes.length);
            System.arraycopy(bytes, 0, buffer, length, bytes.length);
            length += bytes.length;
            return this;
        }

        Writer raw(char c) {
            ensure(1);
            buffer[length++] = (byte) c;
            return this;
        }

        Writer number(long value) {
            if (value == Long.MIN_VALUE) {
                return raw(bytes(Long.toString(value)));
            }
            ensure(20);
            if (value < 0) {
                buffer[length++] = '-';
                value = -value;
            }
            int digits = 1;
            for (long rest = value / 10; rest > 0; rest /= 10) {
                digits++;
            }
            for (int i = length + digits - 1; i >= length; i--) {
                buffer[i] = (byte) ('0' + value % 10);
                value /= 10;
            }
            length += digits;
            return this;
        }

        Writer text(String value) {
            for (int i = 0; i < value.length(); i++) {
                if (!plain(value.charAt(i))) {
                    return raw(bytes(URLEncoder.encode(value, StandardCharsets.UTF_8)));
                }
            }
            ensure(value.length());
            for (int i = 0; i < value.length(); i++) {
                buffer[length++] = (byte) value.charAt(i);
            }
            return this;
        }

        Writer uuid(UUID value) {
            ensure(36);
            hex(value.getMostSignificantBits() >>> 32, 8).raw('-');
            hex(value.getMostSignificantBits() >>> 16, 4).raw('-');
            hex(value.getMostSignificantBits(), 4).raw('-');
            hex(value.getLeastSignificantBits() >>> 48, 4).raw('-');
            return hex(value.getLeastSignificantBits(), 12);
        }

        private Writer hex(long value, int digits) {
            ensure(digits);
            for (int i = digits - 1; i >= 0; i--) {
                buffer[length + i] = HEX[(int) (value & 0xf)];
                value >>>= 4;
            }
            length += digits;
            return this;
        }

        byte[] toBytes() {
            return Arrays.copyOf(buffer, length);
        }
    }
}
