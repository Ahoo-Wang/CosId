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

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author ahoo wang
 */
class Radix36IdConverterTest {

    @ParameterizedTest
    @ValueSource(longs = {1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asString(long argId) {
        String idStr = Radix36IdConverter.INSTANCE.asString(argId);
        Assertions.assertNotNull(idStr);
        Assertions.assertTrue(idStr.length() <= Radix36IdConverter.MAX_CHAR_SIZE);
    }
    
    @Test
    void asStringWhenIdNegative() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Radix36IdConverter.INSTANCE.asString(-1L);
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asLong(long argId) {
        String idStr = Radix36IdConverter.INSTANCE.asString(argId);
        long actual = Radix36IdConverter.INSTANCE.asLong(idStr);
        Assertions.assertEquals(argId, actual);
    }

    @Test
    void asLongWhenNumberFormat() {
        int charSize = 2;
        Radix36IdConverter idConvert = Radix36IdConverter.of(false, charSize);

        Assertions.assertThrows(NumberFormatException.class, () -> {
            idConvert.asLong("-1");
        });
        Assertions.assertThrows(NumberFormatException.class, () -> {
            idConvert.asLong("111");
        });
        Assertions.assertThrows(NumberFormatException.class, () -> {
            idConvert.asLong("1_");
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asStringPad(long argId) {
        String idStr = Radix36IdConverter.PAD_START.asString(argId);
        Assertions.assertNotNull(idStr);
        Assertions.assertEquals(Radix36IdConverter.MAX_CHAR_SIZE, idStr.length());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 5, 62, 63, 124, Integer.MAX_VALUE, Long.MAX_VALUE})
    void asLongPad(long argId) {
        String idStr = Radix36IdConverter.PAD_START.asString(argId);
        long actual = Radix36IdConverter.PAD_START.asLong(idStr);
        Assertions.assertEquals(argId, actual);
    }

    @Test
    void asStringSnowflakeId() {
        IdGenerator idGenerator = new MillisecondSnowflakeId(1);
        long argId = idGenerator.generate();
        String idStr = Radix36IdConverter.PAD_START.asString(argId);
        Assertions.assertNotNull(idStr);
        Assertions.assertEquals(Radix36IdConverter.MAX_CHAR_SIZE, idStr.length());
    }

    @Test
    void asStringCharSize12() {
        int charSize = 12;
        Radix36IdConverter idConvert = Radix36IdConverter.of(false, charSize);
        long maxId = Double.valueOf(Math.pow(Radix36IdConverter.RADIX, charSize)).longValue();
        Assertions.assertThrows(IllegalArgumentException.class, () -> idConvert.asString(maxId));
        long id = maxId - 1;
        String actualIdStr = idConvert.asString(id);
        Assertions.assertEquals(charSize, actualIdStr.length());
        long actualId = idConvert.asLong(actualIdStr);
        Assertions.assertEquals(id, actualId);

        actualIdStr = idConvert.asString(1L);
        Assertions.assertEquals(1, actualIdStr.length());
    }

    @Test
    void asStringPadCharSize12() {
        int charSize = 12;
        Radix36IdConverter idConvert = Radix36IdConverter.of(true, charSize);
        String actualIdStr = idConvert.asString(1L);
        Assertions.assertEquals(charSize, actualIdStr.length());
    }

}
