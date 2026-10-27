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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.time.Layout;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

class CodecModelTest {
    @Test
    void friendlyAndDatePrefixKeepTheirConfiguration() {
        Friendly friendly = Friendly.of(Layout.SNOWFLAKE_MS, ZoneOffset.UTC, true);
        assertThat(friendly.layout(), is(Layout.SNOWFLAKE_MS));
        assertThat(friendly.zone(), is(ZoneOffset.UTC));
        assertThat(friendly.padStart(), is(true));
        DatePrefix prefix = DatePrefix.of("yyyyMMdd", "-", DatePrefix.Source.ID_TIME);
        assertThat(prefix.pattern(), is("yyyyMMdd"));
        assertThat(prefix.delimiter(), is("-"));
        assertThat(prefix.source(), is(DatePrefix.Source.ID_TIME));
        assertThrows(IllegalArgumentException.class, () -> DatePrefix.of("", "-", DatePrefix.Source.NOW));
        Radix radix = Radix.of(62, 11, false);
        assertThat(radix.radix(), is(62));
        assertThat(radix.width(), is(11));
        assertThat(radix.padStart(), is(false));
    }
}
