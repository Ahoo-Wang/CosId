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

package me.ahoo.cosid.jackson;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.jackson.dto.RadixDto;
import me.ahoo.cosid.jackson.dto.RadixPadSize5Dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * What {@link AsStringDeserializer} accepts: by JSON token type.
 */
class AsStringInputTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void aNumberIsTheIdItself() {
        // Before 4.1 the number 123 was read as the Radix62 string "123".
        RadixDto dto = objectMapper.readValue("{\"objectLong\":123}", RadixDto.class);
        Assertions.assertEquals(123L, dto.getObjectLong());
    }
    
    @Test
    void aStringIsConverted() {
        String radix = Radix62IdConverter.INSTANCE.asString(123L);
        RadixDto dto = objectMapper.readValue("{\"objectLong\":\"" + radix + "\"}", RadixDto.class);
        Assertions.assertEquals(123L, dto.getObjectLong());
    }
    
    @Test
    void nullAndEmptyAreNull() {
        Assertions.assertNull(objectMapper.readValue("{\"objectLong\":null}", RadixDto.class).getObjectLong());
        Assertions.assertNull(objectMapper.readValue("{\"objectLong\":\"\"}", RadixDto.class).getObjectLong());
    }
    
    @Test
    void anInvalidStringIsAnInvalidFormat() {
        InvalidFormatException invalid = Assertions.assertThrows(InvalidFormatException.class,
            () -> objectMapper.readValue("{\"objectLong\":\"!!\"}", RadixDto.class));
        Assertions.assertEquals("!!", invalid.getValue());
        Assertions.assertEquals(Long.class, invalid.getTargetType());
    }
    
    @Test
    void otherTokensAreMismatchedInput() {
        Assertions.assertThrows(MismatchedInputException.class, () -> objectMapper.readValue("{\"objectLong\":1.5}", RadixDto.class));
        Assertions.assertThrows(MismatchedInputException.class, () -> objectMapper.readValue("{\"objectLong\":true}", RadixDto.class));
    }
    
    @Test
    void withoutAPropertyTheConverterIsKept() {
        AsStringDeserializer deserializer = new AsStringDeserializer();
        Assertions.assertSame(deserializer, deserializer.createContextual(null, null));
        AsStringSerializer serializer = new AsStringSerializer();
        Assertions.assertSame(serializer, serializer.createContextual(null, null));
    }
    
    @Test
    void aNumberOutOfTheConvertersRangeIsAnInvalidFormat() {
        InvalidFormatException negative = Assertions.assertThrows(InvalidFormatException.class,
            () -> objectMapper.readValue("{\"objectLong\":-5}", RadixDto.class));
        Assertions.assertEquals(-5L, negative.getValue());
        // radixCharSize = 5: Long.MAX_VALUE has more Radix62 digits.
        Assertions.assertThrows(InvalidFormatException.class,
            () -> objectMapper.readValue("{\"objectLong\":" + Long.MAX_VALUE + "}", RadixPadSize5Dto.class));
    }
}
