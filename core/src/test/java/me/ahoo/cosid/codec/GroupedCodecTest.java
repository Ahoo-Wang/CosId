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

import me.ahoo.cosid.GroupedId;

import org.junit.jupiter.api.Test;

class GroupedCodecTest {
    /** A padded decimal codec, so this test does not depend on Radix (R1-A). */
    private static final IdCodec DECIMAL6 = new IdCodec() {
        @Override
        public String encode(long id) {
            return String.format("%06d", id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text);
        }
    };

    @Test
    void periodDelimiterValue() {
        GroupedCodec codec = GroupedCodec.datePrefix("-", DECIMAL6);
        assertThat(codec.encode(new GroupedId("20261026", 42)), is("20261026-000042"));
        assertThat(codec.decode("20261026-000042"), is(new GroupedId("20261026", 42)));
    }

    @Test
    void splitsAtTheFirstDelimiterSoTheValueCodecMayContainIt() {
        GroupedCodec codec = GroupedCodec.datePrefix("-", DECIMAL6.withPrefix("ORD-"));
        assertThat(codec.encode(new GroupedId("202610", 7)), is("202610-ORD-000007"));
        assertThat(codec.decode("202610-ORD-000007"), is(new GroupedId("202610", 7)));
    }

    @Test
    void composesWithBusinessPrefixes() {
        GroupedCodec codec = GroupedCodec.datePrefix("", DECIMAL6);
        assertThat(codec.encode(new GroupedId("2026", 1)), is("2026000001"));
        assertThrows(UnsupportedOperationException.class, () -> codec.decode("2026000001"));
    }

    @Test
    void refusesTextWithoutThePeriod() {
        GroupedCodec codec = GroupedCodec.datePrefix("-", DECIMAL6);
        assertThrows(IllegalArgumentException.class, () -> codec.decode("000042"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("-000042"));
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix(null, DECIMAL6));
        assertThrows(NullPointerException.class, () -> GroupedCodec.datePrefix("-", null));
    }
}
