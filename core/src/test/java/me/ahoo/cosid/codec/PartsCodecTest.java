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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

class PartsCodecTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static IdParts random(Layout layout, Random random) {
        return new IdParts(random.nextLong() >>> (64 - layout.timeBits()), layout.nodeBits() == 0 ? 0 : random.nextInt(1 << layout.nodeBits()),
            random.nextLong() >>> (64 - layout.seqBits()));
    }

    @Test
    void radix62OfCosId80() {
        PartsCodec codec = PartsCodec.radix(Layout.COSID_80, 62);
        long millis = Instant.parse("2026-10-26T04:34:56.789Z").toEpochMilli();
        String text = codec.encode(new IdParts(millis, 1, 7));
        // 8 + 4 + 3 digits, as the 4.x Radix62CosIdGenerator.
        assertThat(text.length(), is(15));
        assertThat(text, is(Radix.of(62, 8, true).encode(millis) + "0001" + "007"));
        assertThat(codec.decode(text), is(new IdParts(millis, 1, 7)));
        assertThat(PartsCodec.radix(Layout.COSID_80, 36).encode(new IdParts(0, 0, 0)), is("0".repeat(9 + 4 + 4)));
    }

    @Test
    void radixRoundTripsAndSorts() {
        Random random = new Random(5);
        Layout noNode = new Layout(Instant.EPOCH, ChronoUnit.MILLIS, 44, 0, 16, 0);
        for (Layout layout : new Layout[] {Layout.COSID_80, Layout.SNOWFLAKE_MS, noNode}) {
            for (int radix = Radix.MIN_RADIX; radix <= Radix.MAX_RADIX; radix++) {
                PartsCodec codec = PartsCodec.radix(layout, radix);
                IdParts previous = null;
                String previousText = null;
                for (int i = 0; i < 500; i++) {
                    IdParts parts = random(layout, random);
                    String text = codec.encode(parts);
                    assertThat(codec.decode(text), is(parts));
                    if (previous != null && radix == 62) {
                        int byParts = Long.compare(previous.time(), parts.time()) != 0 ? Long.compare(previous.time(), parts.time())
                            : Integer.compare(previous.node(), parts.node()) != 0 ? Integer.compare(previous.node(), parts.node())
                            : Long.compare(previous.seq(), parts.seq());
                        assertThat(Integer.signum(previousText.compareTo(text)), is(Integer.signum(byParts)));
                    }
                    previous = parts;
                    previousText = text;
                }
                IdParts max = new IdParts(~(-1L << layout.timeBits()), ~(-1 << layout.nodeBits()), ~(-1L << layout.seqBits()));
                assertThat(codec.decode(codec.encode(max)), is(max));
            }
        }
    }

    @Test
    void radixRejectsInvalidInput() {
        PartsCodec codec = PartsCodec.radix(Layout.COSID_80, 62);
        assertThrows(IllegalArgumentException.class, () -> PartsCodec.radix(Layout.COSID_80, 63));
        assertThrows(IllegalArgumentException.class, () -> PartsCodec.radix(Layout.COSID_80, 1));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new IdParts(1L << 44, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new IdParts(0, 1 << 20, 0)));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new IdParts(0, 0, 1 << 16)));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new IdParts(-1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("0000000000000"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("00000000zzzz000"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("zzzzzzzz0000000"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("00000000000-000"));
    }

    @Test
    void friendlyOfCosId80() {
        PartsCodec codec = PartsCodec.friendly(Layout.COSID_80, SHANGHAI);
        long millis = Instant.parse("2026-10-26T04:34:56.789Z").toEpochMilli();
        assertThat(codec.encode(new IdParts(millis, 1, 7)), is("20261026123456789-0000001-00007"));
        assertThat(codec.decode("20261026123456789-0000001-00007"), is(new IdParts(millis, 1, 7)));
        Random random = new Random(9);
        for (int i = 0; i < 2_000; i++) {
            IdParts parts = new IdParts(random.nextLong() >>> 20, random.nextInt(1 << 20),
                random.nextInt(1 << 16));
            assertThat(codec.decode(codec.encode(parts)), is(parts));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new IdParts(1L << 44, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("20261026123456789-1048576-00007"));
    }

    @Test
    void friendlyWithoutPadding() {
        PartsCodec codec = PartsCodec.friendly(Layout.COSID_80, SHANGHAI, false);
        long millis = Instant.parse("2026-10-26T04:34:56.789Z").toEpochMilli();
        assertThat(codec.encode(new IdParts(millis, 1, 7)), is("20261026123456789-1-7"));
        assertThat(codec.decode("20261026123456789-1-7"), is(new IdParts(millis, 1, 7)));
        assertThat(codec.encode(new IdParts(millis, 0, 0)), is("20261026123456789-0-0"));
        assertThat(codec.decode("20261026123456789-0-0"), is(new IdParts(millis, 0, 0)));
        Random random = new Random(11);
        for (int i = 0; i < 2_000; i++) {
            IdParts parts = new IdParts(random.nextLong() >>> 20, random.nextInt(1 << 20), random.nextInt(1 << 16));
            assertThat(codec.decode(codec.encode(parts)), is(parts));
        }
    }

    @Test
    void codecsFollowACustomCosId80Layout() {
        Layout wide = new Layout(Instant.EPOCH, ChronoUnit.MILLIS, 44, 12, 24, Layout.defaultSeqResetThreshold(24));
        Random random = new Random(13);
        for (PartsCodec codec : List.of(PartsCodec.radix(wide, 62), PartsCodec.radix(wide, 36), PartsCodec.friendly(wide, SHANGHAI),
            PartsCodec.friendly(wide, SHANGHAI, false))) {
            for (int i = 0; i < 1_000; i++) {
                IdParts parts = new IdParts(random.nextLong() >>> 20, random.nextInt(1 << 12), random.nextInt(1 << 24));
                assertThat(codec.decode(codec.encode(parts)), is(parts));
            }
            assertThrows(IllegalArgumentException.class, () -> codec.encode(new IdParts(0, 1 << 12, 0)));
        }
    }
}
