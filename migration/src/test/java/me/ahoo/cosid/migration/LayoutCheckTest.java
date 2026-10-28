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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

class LayoutCheckTest {
    private static final long W = 1_793_491_210_000L;

    @Test
    void theSameLayoutPasses() {
        LayoutCheck check = LayoutCheck.check("snow", Layout.SNOWFLAKE_MS, Layout.SNOWFLAKE_MS, W);
        assertThat(check.ok(), is(true));
        assertThat(check.source(), is("epoch=1577203200000 unit=MILLIS bits=41/10/12"));
    }

    @Test
    void anOlderEpochAheadOfTheOldIdsPasses() {
        // 5.0 with an epoch 10 years earlier: its ids at W are larger than every 4.x id up to W.
        Layout earlier = LayoutCheck.snowflake(Layout.COSID_EPOCH.toEpochMilli() - 315_360_000_000L, false, 41, 10, 12);
        assertThat(LayoutCheck.check("snow", Layout.SNOWFLAKE_MS, earlier, W).ok(), is(true));
    }

    @Test
    void aLaterEpochFails() {
        Layout later = LayoutCheck.snowflake(Layout.COSID_EPOCH.toEpochMilli() + 31_536_000_000L, false, 41, 10, 12);
        LayoutCheck check = LayoutCheck.check("snow", Layout.SNOWFLAKE_MS, later, W);
        assertThat(check.ok(), is(false));
        assertThat(check.reason(), containsString("keep the 4.x layout"));
        assertThat(LayoutCheck.check("snow", Layout.SNOWFLAKE_MS, Layout.JS_SAFE_MS, W).ok(), is(false));
        // Seconds since the same epoch, 22 sequence bits: ids at W outgrow the millisecond ones.
        assertThat(LayoutCheck.check("snow", Layout.SNOWFLAKE_MS, Layout.SNOWFLAKE_S, W).ok(), is(true));
    }

    @Test
    void eightyBitLayoutsMustBeEqual() {
        assertThat(LayoutCheck.check("cosid", Layout.COSID_80, Layout.COSID_80, W).ok(), is(true));
        assertThat(LayoutCheck.check("cosid", Layout.COSID_80, Layout.COSID_80.withNodeBits(19), W).ok(), is(false));
    }

    @Test
    void aSequenceResetThresholdAloneIsTheSameLayout() {
        // R1-x-04: the threshold moves where a time unit starts, inside the same id space.
        Layout scattered = new Layout(Layout.SNOWFLAKE_MS.epoch(), Layout.SNOWFLAKE_MS.unit(), 41, 10, 12, 100);
        LayoutCheck check = LayoutCheck.check("snow", scattered, Layout.SNOWFLAKE_MS, W);
        assertThat(check.ok(), is(true));
        assertThat(check.reason(), containsString("the sequence reset threshold differs (4.x 100, 5.0 2047)"));
        Layout cosid = new Layout(Layout.COSID_80.epoch(), Layout.COSID_80.unit(), 44, 20, 16, 7);
        assertThat(LayoutCheck.check("cosid", cosid, Layout.COSID_80, W).ok(), is(true));
        assertThat(LayoutCheck.sameIdSpace(cosid, Layout.COSID_80.withNodeBits(19)), is(false));
        // Each part of the id space counts.
        assertThat(LayoutCheck.sameIdSpace(Layout.SNOWFLAKE_MS, LayoutCheck.snowflake(0, false, 41, 10, 12)), is(false));
        assertThat(LayoutCheck.sameIdSpace(Layout.SNOWFLAKE_MS, LayoutCheck.snowflake(Layout.COSID_EPOCH.toEpochMilli(), true, 41, 10, 12)), is(false));
        assertThat(LayoutCheck.sameIdSpace(Layout.SNOWFLAKE_MS, LayoutCheck.snowflake(Layout.COSID_EPOCH.toEpochMilli(), false, 40, 10, 12)), is(false));
        assertThat(LayoutCheck.sameIdSpace(Layout.SNOWFLAKE_MS, LayoutCheck.snowflake(Layout.COSID_EPOCH.toEpochMilli(), false, 41, 10, 13)), is(false));
    }

    @Test
    void aWBeforeTheEpochFails() {
        Layout future = LayoutCheck.snowflake(W + 1000, false, 41, 10, 12);
        assertThat(LayoutCheck.check("snow", Layout.SNOWFLAKE_MS, future, W).reason(), containsString("W does not fit"));
    }
}
