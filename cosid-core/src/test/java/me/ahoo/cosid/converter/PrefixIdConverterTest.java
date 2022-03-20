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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author rocher kong
 */
class PrefixIdConverterTest {
   private String prefix = "no_";
   private final PrefixIdConverter prefixIdConverter = new PrefixIdConverter(prefix, ToStringIdConverter.INSTANCE);

    @ParameterizedTest
    @ValueSource(strings = {"no_1","no_100","no_1000"})
    void asString(String argId) {
        long idStr = prefixIdConverter.asLong(argId);
        Assertions.assertNotNull(idStr);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1,1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asLong(long argId) {
        String idStr = prefixIdConverter.asString(argId);
        long actual = prefixIdConverter.asLong(idStr);
        Assertions.assertEquals(argId, actual);
    }

    @Test
    void asLongWhenNumberFormat() {
        Assertions.assertDoesNotThrow(() -> {
            prefixIdConverter.asLong("no_-1");
        });
        Assertions.assertThrows(StringIndexOutOfBoundsException.class,() -> {
            prefixIdConverter.asLong("-1");
        });
        Assertions.assertDoesNotThrow(() -> {
            prefixIdConverter.asLong("no_111");
        });
        Assertions.assertThrows(NumberFormatException.class, () -> {
            prefixIdConverter.asLong("no_1_");
        });
    }

}
