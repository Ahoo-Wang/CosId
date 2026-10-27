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

import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeSource;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

class DatePrefixTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final long AT = Instant.parse("2026-10-26T20:34:56.789Z").toEpochMilli();
    private static final TimeSource FIXED = () -> AT;

    private static long snowflake(long millis) {
        return Layout.SNOWFLAKE_MS.pack(millis - Layout.COSID_EPOCH.toEpochMilli(), 1, 7);
    }

    @Test
    void idTimePrefix() {
        IdCodec codec = DatePrefix.of("yyyyMMdd", "-", DatePrefix.Source.ID_TIME)
            .apply(Radix.of(62, 11, true), SHANGHAI, TimeSource.SYSTEM, Optional.of(Layout.SNOWFLAKE_MS));
        long id = snowflake(AT);
        String text = codec.encode(id);
        assertThat(text, is("20261027-" + Radix.of(62, 11, true).encode(id)));
        assertThat(codec.decode(text), is(id));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(-1));
    }

    @Test
    void nowPrefixWithEveryFixedField() {
        IdCodec codec = DatePrefix.of("yy.yyyy/MM/dd HH:mm:ss.SSS uuuu", "", DatePrefix.Source.NOW)
            .apply(Radix.of(10, 6, true), ZoneOffset.UTC, FIXED, Optional.empty());
        assertThat(codec.encode(42), is("26.2026/10/26 20:34:56.789 2026000042"));
        assertThat(codec.decode("26.2026/10/26 20:34:56.789 2026000042"), is(42L));
        assertThat(codec.decode("99.1999/01/01 00:00:00.000 1999000043"), is(43L));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("26.2026-10/26 20:34:56.789 2026000042"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("26.2026/1x/26 20:34:56.789 2026000042"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("26.2026"));
    }

    @Test
    void otherPatternsUseTheFormatter() {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
        try {
            IdCodec codec = DatePrefix.of("yyyy-MMM-d", "_", DatePrefix.Source.NOW)
                .apply(Radix.of(10, 6, false), SHANGHAI, FIXED, Optional.empty());
            String text = codec.encode(42);
            assertThat(text, is("2026-Oct-27_42"));
            assertThat(codec.decode(text), is(42L));
            assertThat(codec.decode("2026-Jan-1_43"), is(43L));
            assertThrows(IllegalArgumentException.class, () -> codec.decode("2026-Oct-27-42"));
            assertThrows(IllegalArgumentException.class, () -> codec.decode("x_42"));
            IdCodec quoted = DatePrefix.of("'D'yyyyMMdd", "", DatePrefix.Source.NOW)
                .apply(Radix.of(10, 6, true), SHANGHAI, FIXED, Optional.empty());
            assertThat(quoted.encode(1), is("D20261027000001"));
            assertThat(quoted.decode("D20261027000001"), is(1L));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void composesWithPrefixAndSuffix() {
        IdCodec codec = DatePrefix.of("yyyyMMdd", "", DatePrefix.Source.NOW)
            .apply(Radix.of(10, 6, true), SHANGHAI, FIXED, Optional.empty())
            .withPrefix("ORD-").withSuffix("!");
        assertThat(codec.encode(5), is("ORD-20261027000005!"));
        assertThat(codec.decode("ORD-20261027000005!"), is(5L));
    }

    @Test
    void idTimeNeedsALongLayout() {
        DatePrefix prefix = DatePrefix.of("yyyyMMdd", "-", DatePrefix.Source.ID_TIME);
        IdCodec radix = Radix.of(62, 11, true);
        assertThrows(IllegalArgumentException.class, () -> prefix.apply(radix, SHANGHAI, FIXED, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> prefix.apply(radix, SHANGHAI, FIXED, Optional.of(Layout.COSID_80)));
        assertThrows(NullPointerException.class, () -> prefix.apply(null, SHANGHAI, FIXED, Optional.empty()));
    }
}
