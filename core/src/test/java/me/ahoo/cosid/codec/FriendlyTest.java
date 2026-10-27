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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;

class FriendlyTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MILLIS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static long id(Layout layout, String instant, int node, long seq) {
        long sinceEpoch = Instant.parse(instant).toEpochMilli() - layout.epoch().toEpochMilli();
        return layout.pack(sinceEpoch / layout.unit().getDuration().toMillis(), node, seq);
    }

    @Test
    void knownValues() {
        long id = id(Layout.SNOWFLAKE_MS, "2026-10-26T04:34:56.789Z", 1, 7);
        assertThat(Friendly.of(Layout.SNOWFLAKE_MS, SHANGHAI, true).encode(id), is("20261026123456789-0001-0007"));
        assertThat(Friendly.of(Layout.SNOWFLAKE_MS, SHANGHAI, false).encode(id), is("20261026123456789-1-7"));
        assertThat(Friendly.of(Layout.SNOWFLAKE_MS, ZoneOffset.UTC, true).encode(id), is("20261026043456789-0001-0007"));
        long seconds = id(Layout.SNOWFLAKE_S, "2026-10-26T04:34:56Z", 1023, 4_194_303);
        assertThat(Friendly.of(Layout.SNOWFLAKE_S, SHANGHAI, true).encode(seconds), is("20261026123456-1023-4194303"));
        assertThat(Friendly.of(Layout.SNOWFLAKE_MS, SHANGHAI, true).decode("20261026123456789-1-7"), is(id));
    }

    @Test
    void matchesDateTimeFormatterAndRoundTrips() {
        Random random = new Random(11);
        ZoneId[] zones = {SHANGHAI, ZoneOffset.UTC, ZoneId.of("America/New_York"), ZoneId.of("Europe/London"),
            ZoneId.of("Australia/Lord_Howe"), ZoneOffset.ofHoursMinutes(-9, -30)};
        for (Layout layout : new Layout[] {Layout.SNOWFLAKE_MS, Layout.SNOWFLAKE_S, Layout.JS_SAFE_MS}) {
            for (ZoneId zone : zones) {
                for (boolean pad : new boolean[] {true, false}) {
                    Friendly codec = Friendly.of(layout, zone, pad);
                    DateTimeFormatter formatter = (layout.unit().getDuration().toMillis() == 1 ? MILLIS : SECONDS).withZone(zone);
                    for (int i = 0; i < 3_000; i++) {
                        long id = random.nextLong() >>> (64 - layout.totalBits());
                        IdParts parts = layout.unpack(id);
                        String text = codec.encode(id);
                        Instant instant = parts.instant(layout);
                        assertThat(text, is(formatter.format(instant) + "-" + node(parts, layout, pad) + "-" + seq(parts, layout, pad)));
                        LocalDateTime local = LocalDateTime.ofInstant(instant, zone);
                        if (zone.getRules().getValidOffsets(local).size() == 1) {
                            assertThat(text, codec.decode(text), is(id));
                        }
                    }
                }
            }
        }
    }

    @Test
    void roundTripsAroundTransitions() {
        ZoneId newYork = ZoneId.of("America/New_York");
        Friendly codec = Friendly.of(Layout.SNOWFLAKE_MS, newYork, true);
        for (String at : new String[] {"2026-03-08T06:59:59.999Z", "2026-03-08T07:00:00Z", "2026-11-01T05:59:59.999Z", "2026-11-01T07:00:00Z",
            "2026-06-01T00:00:00Z"}) {
            long id = id(Layout.SNOWFLAKE_MS, at, 3, 9);
            assertThat(at, codec.decode(codec.encode(id)), is(id));
        }
        // A local time in an overlap is the earlier instant, like ZonedDateTime.of: the later one does not round trip.
        long overlap = id(Layout.SNOWFLAKE_MS, "2026-11-01T06:00:00Z", 3, 9);
        assertThat(codec.decode(codec.encode(overlap)), is(id(Layout.SNOWFLAKE_MS, "2026-11-01T05:00:00Z", 3, 9)));
        // A local time in a gap is resolved like ZonedDateTime.of: shifted by the gap.
        long gap = codec.decode("20260308023000000-0003-0009");
        assertThat(Layout.SNOWFLAKE_MS.unpack(gap).instant(Layout.SNOWFLAKE_MS), is(Instant.parse("2026-03-08T07:30:00Z")));
    }

    private static String node(IdParts parts, Layout layout, boolean pad) {
        return pad(Long.toString(parts.node()), Long.toString((1L << layout.nodeBits()) - 1).length(), pad);
    }

    private static String seq(IdParts parts, Layout layout, boolean pad) {
        return pad(Long.toString(parts.seq()), Long.toString((1L << layout.seqBits()) - 1).length(), pad);
    }

    private static String pad(String text, int width, boolean pad) {
        return pad ? "0".repeat(width - text.length()) + text : text;
    }

    @Test
    void rejectsInvalidText() {
        Friendly codec = Friendly.of(Layout.SNOWFLAKE_MS, SHANGHAI, true);
        for (String text : new String[] {"", "20261026123456789", "20261026123456789-1", "20261026123456789_1_1", "2026102612345678X-1-1",
            "20261326123456789-1-1", "20260230123456789-1-1", "20261026243456789-1-1", "20261026123456789-1024-1", "20261026123456789-1-4096",
            "20261026123456789--1", "20261026123456789-1-", "20261026123456789-1-1-1", "20191224000000000-1-1", "20261026123456789-1-99999999999999999999",
            "20261026123456789-1-x"}) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(text), text);
        }
        assertThrows(IllegalArgumentException.class, () -> codec.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> Friendly.of(Layout.JS_SAFE_MS, SHANGHAI, true).encode(1L << 53));
        assertThrows(IllegalArgumentException.class, () -> Friendly.of(Layout.COSID_80, SHANGHAI, true));
        assertThrows(IllegalArgumentException.class, () -> Friendly.of(Layout.SNOWFLAKE_S, SHANGHAI, true).decode("20261026123456789-1-1"));
    }
}
