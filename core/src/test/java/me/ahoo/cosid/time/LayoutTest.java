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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class LayoutTest {
    @Test
    void presetsKeepThe4xLayouts() {
        assertThat(Layout.SNOWFLAKE_MS.epoch().toEpochMilli(), is(1577203200000L));
        assertThat(Layout.SNOWFLAKE_MS.totalBits(), is(63));
        assertThat(Layout.SNOWFLAKE_MS.seqResetThreshold(), is(2047L));
        assertThat(Layout.SNOWFLAKE_S.unit(), is(ChronoUnit.SECONDS));
        assertThat(Layout.SNOWFLAKE_S.totalBits(), is(63));
        assertThat(Layout.JS_SAFE_MS.totalBits(), is(53));
        assertThat(Layout.JS_SAFE_S.totalBits(), is(53));
        assertThat(Layout.COSID_80.totalBits(), is(80));
        assertThat(Layout.COSID_80.epoch(), is(Instant.EPOCH));
    }

    @Test
    void jsSafety() {
        assertThat(Layout.JS_SAFE_MS.isJsSafe(), is(true));
        assertThat(Layout.SNOWFLAKE_MS.isJsSafe(), is(false));
        assertThat(Layout.isJsSafe(9007199254740991L), is(true));
        assertThat(Layout.isJsSafe(9007199254740992L), is(false));
        assertThat(Layout.isJsSafe(-1), is(false));
    }

    @Test
    void withNodeBitsChangesOnlyTheNodePart() {
        Layout layout = Layout.SNOWFLAKE_MS.withNodeBits(8);
        assertThat(layout.nodeBits(), is(8));
        assertThat(layout.timeBits(), is(41));
        assertThat(layout.seqBits(), is(12));
        assertThrows(IllegalArgumentException.class, () -> Layout.SNOWFLAKE_MS.withNodeBits(11));
        assertThat(Layout.COSID_80.withNodeBits(21).totalBits(), is(81));
    }

    @Test
    void rejectsInvalidLayouts() {
        Instant epoch = Layout.COSID_EPOCH;
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch, ChronoUnit.MINUTES, 41, 10, 12, 0));
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch.plusMillis(1), ChronoUnit.SECONDS, 31, 10, 22, 0));
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch, ChronoUnit.MILLIS, 0, 10, 12, 0));
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch, ChronoUnit.MILLIS, 41, 32, 12, 0));
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch, ChronoUnit.MILLIS, 41, 10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch, ChronoUnit.MILLIS, 41, 10, 12, 4096));
        assertThrows(IllegalArgumentException.class, () -> new Layout(epoch, ChronoUnit.MILLIS, 41, 10, 12, -1));
    }

    @Test
    void partsInstant() {
        IdParts parts = new IdParts(1000, 1, 2);
        assertThat(parts.instant(Layout.SNOWFLAKE_MS), is(Layout.COSID_EPOCH.plusMillis(1000)));
        assertThat(parts.instant(Layout.SNOWFLAKE_S), is(Layout.COSID_EPOCH.plusSeconds(1000)));
    }
}
