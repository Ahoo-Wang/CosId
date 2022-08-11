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

package me.ahoo.cosid.converter;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RadixIdConverterTest {
    
    @ParameterizedTest
    @CsvSource({
        "0,0",
        "9,9",
        "A,10",
        "Z,35",
        "a,36",
        "z,61",
        "-,-1",
    })
    void offset(char digitChar, int expected) {
        int actual = RadixIdConverter.offset(digitChar);
        assertThat(actual, equalTo(expected));
    }
    
    @ParameterizedTest
    @CsvSource({
        "62,16,3",
        "62,40,7",
        "62,44,8",
        "62,48,9",
        "36,16,4",
        "36,40,8",
        "36,44,9",
        "36,48,10",
    })
    void maxCharSizeRadix(int radix, int bits, int expected) {
        int charSize = RadixIdConverter.maxCharSize(radix, bits);
        assertThat(charSize, equalTo(expected));
    }
}
