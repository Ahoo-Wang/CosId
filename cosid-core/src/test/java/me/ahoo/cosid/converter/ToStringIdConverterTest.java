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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.stat.converter.ToStringConverterStat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author rocher kong
 */
class ToStringIdConverterTest {
    
    @ParameterizedTest
    @ValueSource(longs = {1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asString(long argId) {
        String idStr = ToStringIdConverter.INSTANCE.asString(argId);
        Assertions.assertNotNull(idStr);
    }
    
    @ParameterizedTest
    @ValueSource(longs = {1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asLong(long argId) {
        String idStr = ToStringIdConverter.INSTANCE.asString(argId);
        long actual = ToStringIdConverter.INSTANCE.asLong(idStr);
        Assertions.assertEquals(argId, actual);
    }
    
    @Test
    void asLongWhenNumberFormat() {
        ToStringIdConverter idConvert = ToStringIdConverter.INSTANCE;
        
        Assertions.assertDoesNotThrow(() -> {
            idConvert.asLong("-1");
        });
        Assertions.assertDoesNotThrow(() -> {
            idConvert.asLong("111");
        });
        Assertions.assertThrows(NumberFormatException.class, () -> {
            idConvert.asLong("1_");
        });
    }
    
    @Test
    void asStringWithPadStart() {
        ToStringIdConverter idConvert = new ToStringIdConverter(true, 5);
        Assertions.assertEquals("00001", idConvert.asString(1));
        Assertions.assertEquals(1, idConvert.asLong("00001"));
    }
    
    @Test
    void stat() {
        assertThat(ToStringIdConverter.INSTANCE.stat(), instanceOf(ToStringConverterStat.class));
    }
}
