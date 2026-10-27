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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.lease.Owner;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The byte codec writes exactly what {@link URLEncoder}, {@link Long#toString} and {@link UUID#toString} would, so
 * nodes written before it stay readable and stay plain text.
 */
class AsciiTest {
    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void textMatchesUrlEncoding() {
        for (String value : new String[] {"host-a", "pod_1.ns*", "a b", "é=%/\n|+", ""}) {
            byte[] written = new Ascii.Writer(4).text(value).toBytes();
            assertArrayEquals(ascii(URLEncoder.encode(value, StandardCharsets.UTF_8)), written, value);
            assertEquals(value, Ascii.text(written, 0, written.length));
        }
    }

    @Test
    void numbersRoundTrip() {
        for (long value : new long[] {0, 7, -7, 1_726_000_000_000L, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1}) {
            byte[] written = new Ascii.Writer(1).number(value).toBytes();
            assertArrayEquals(ascii(Long.toString(value)), written);
            assertEquals(value, Ascii.number(written, 0, written.length));
        }
        assertThrows(NumberFormatException.class, () -> Ascii.number(ascii(""), 0, 0));
        assertThrows(NumberFormatException.class, () -> Ascii.number(ascii("-"), 0, 1));
        assertThrows(NumberFormatException.class, () -> Ascii.number(ascii("1x"), 0, 2));
        assertThrows(ArithmeticException.class, () -> Ascii.number(ascii("99999999999999999999"), 0, 20));
        assertThrows(NumberFormatException.class, () -> Ascii.number(ascii("9223372036854775808"), 0, 19));
    }

    @Test
    void uuidsRoundTrip() {
        for (int i = 0; i < 100; i++) {
            UUID value = UUID.randomUUID();
            byte[] written = new Ascii.Writer(8).uuid(value).toBytes();
            assertArrayEquals(ascii(value.toString()), written);
            assertEquals(value, Ascii.uuid(written, 0, written.length));
        }
        UUID edge = new UUID(-1, 0);
        byte[] written = new Ascii.Writer(8).uuid(edge).toBytes();
        assertEquals(edge, Ascii.uuid(written, 0, written.length));
        assertThrows(IllegalArgumentException.class, () -> Ascii.uuid(ascii("not-a-uuid"), 0, 10));
        byte[] badHex = ascii("zzzzzzzz-0000-0000-0000-000000000000");
        assertThrows(IllegalArgumentException.class, () -> Ascii.uuid(badHex, 0, badHex.length));
    }

    @Test
    void slotTextWrittenByHandStaysReadable() {
        UUID incarnation = UUID.randomUUID();
        String text = "owner=host%2Fa\nincarnation=" + incarnation + "\nlastOwner=host%2Fa\nfence=3\nttl=30000\ntolerance=1000\n"
            + "floor=10\nexpiresMin=20\nceilingMin=30\nnextFloor=0\n";
        SlotState state = SlotState.parse("/p", text.getBytes(StandardCharsets.UTF_8));
        assertEquals(new Owner("host/a", incarnation), state.owner());
        assertEquals(3, state.fence());
        assertEquals(30, state.ceilingMin());
        assertEquals(text, new String(state.bytes(), StandardCharsets.UTF_8), "written back byte for byte");
        assertEquals(new ZookeeperRangeStore.Value(42, true), ZookeeperRangeStore.Value.parse("/r", ascii("42:sealed")));
        assertArrayEquals(ascii("42:sealed"), new ZookeeperRangeStore.Value(42, true).bytes());
        assertArrayEquals(ascii("42"), new ZookeeperRangeStore.Value(42, false).bytes());
    }
}
