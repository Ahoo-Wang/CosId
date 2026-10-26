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

import org.junit.jupiter.api.Test;

class IdCodecTest {
    private static final IdCodec DECIMAL = new IdCodec() {
        @Override
        public String encode(long id) {
            return Long.toString(id);
        }

        @Override
        public long decode(String text) {
            return Long.parseLong(text);
        }
    };

    @Test
    void prefixAndSuffixApplyInOrder() {
        IdCodec codec = DECIMAL.withPrefix("O").withSuffix("X");
        assertThat(codec.encode(42), is("O42X"));
        assertThat(codec.decode("O42X"), is(42L));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("P42X"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("O42Y"));
    }

    @Test
    void radixWidths() {
        assertThat(Radix.maxWidth(62), is(11));
        assertThat(Radix.maxWidth(36), is(13));
        assertThat(Radix.maxWidth(10), is(19));
        assertThat(Radix.maxWidth(2), is(63));
        assertThat(Radix.of(62, 11, true).maxValue(), is(Long.MAX_VALUE));
        assertThat(Radix.of(10, 6, true).maxValue(), is(999999L));
        assertThat(Radix.of(36, 2, false).maxValue(), is(1295L));
    }

    @Test
    void radixRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> Radix.of(1, 1, true));
        assertThrows(IllegalArgumentException.class, () -> Radix.of(63, 1, true));
        assertThrows(IllegalArgumentException.class, () -> Radix.of(62, 0, true));
        assertThrows(IllegalArgumentException.class, () -> Radix.of(62, 12, true));
    }
}
