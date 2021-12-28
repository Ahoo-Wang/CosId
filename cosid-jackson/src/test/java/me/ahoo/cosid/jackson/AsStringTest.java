/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class AsStringTest {
    ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    public void serializeToString() {
        ToStringDto dto = new ToStringDto();
        dto.primitiveLong = 100;
        dto.objectLong = 200L;
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"100\",\"objectLong\":\"200\"}", deStr);
        ToStringDto deDto = objectMapper.readValue(deStr, ToStringDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }

    @SneakyThrows
    @Test
    public void deserializeToString() {
        ToStringDto dto = new ToStringDto();
        dto.primitiveLong = 100;
        dto.objectLong = 200L;
        String deStr = objectMapper.writeValueAsString(dto);
        ToStringDto deDto = objectMapper.readValue(deStr, ToStringDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }

    @SneakyThrows
    @Test
    public void serializeRadix() {
        RadixDto dto = new RadixDto();
        dto.primitiveLong = 100;
        dto.objectLong = 200L;
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"1c\",\"objectLong\":\"3E\"}", deStr);
        RadixDto deDto = objectMapper.readValue(deStr, RadixDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }

    @SneakyThrows
    @Test
    public void deserializeToRadix() {
        RadixDto dto = new RadixDto();
        dto.primitiveLong = 100;
        dto.objectLong = 200L;
        String deStr = objectMapper.writeValueAsString(dto);
        RadixDto deDto = objectMapper.readValue(deStr, RadixDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }

    @SneakyThrows
    @Test
    public void serializeRadixPad() {
        RadixPadDto dto = new RadixPadDto();
        dto.primitiveLong = 100;
        dto.objectLong = 200L;
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"0000000001c\",\"objectLong\":\"0000000003E\"}", deStr);
        RadixPadDto deDto = objectMapper.readValue(deStr, RadixPadDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }

    @SneakyThrows
    @Test
    public void serializeRadixPadSize5() {
        RadixPadSize5Dto dto = new RadixPadSize5Dto();
        dto.primitiveLong = 100;
        dto.objectLong = 200L;
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"0001c\",\"objectLong\":\"0003E\"}", deStr);
        RadixPadSize5Dto deDto = objectMapper.readValue(deStr, RadixPadSize5Dto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }


    @SneakyThrows
    @Test
    public void serializeFriendlyId() {
        FriendlyIdDto dto = new FriendlyIdDto();
        dto.primitiveLong = 266231902451535872L;
        dto.objectLong = 266231902451535873L;
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"20211228155031894-1-0\",\"objectLong\":\"20211228155031894-1-1\"}", deStr);
        FriendlyIdDto deDto = objectMapper.readValue(deStr, FriendlyIdDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong, deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong, deDto.objectLong);
    }

    @SneakyThrows
    @Test
    public void testNull() {

        ToStringDto deDto = objectMapper.readValue("{\"primitiveLong\":\"1\",\"objectLong\":\"2\"}", ToStringDto.class);
        Assertions.assertNotNull(deDto);

    }

    public static class ToStringDto {
        @AsString
        private long primitiveLong;
        @AsString
        private Long objectLong;

        public long getPrimitiveLong() {
            return primitiveLong;
        }

        public void setPrimitiveLong(long primitiveLong) {
            this.primitiveLong = primitiveLong;
        }

        public Long getObjectLong() {
            return objectLong;
        }

        public void setObjectLong(Long objectLong) {
            this.objectLong = objectLong;
        }
    }

    public static class RadixDto {
        @AsString(AsString.Type.RADIX)
        private long primitiveLong;
        @AsString(AsString.Type.RADIX)
        private Long objectLong;

        public long getPrimitiveLong() {
            return primitiveLong;
        }

        public void setPrimitiveLong(long primitiveLong) {
            this.primitiveLong = primitiveLong;
        }

        public Long getObjectLong() {
            return objectLong;
        }

        public void setObjectLong(Long objectLong) {
            this.objectLong = objectLong;
        }
    }

    public static class RadixPadDto {
        @AsString(value = AsString.Type.RADIX, radixPadStart = true)
        private long primitiveLong;
        @AsString(value = AsString.Type.RADIX, radixPadStart = true)
        private Long objectLong;

        public long getPrimitiveLong() {
            return primitiveLong;
        }

        public void setPrimitiveLong(long primitiveLong) {
            this.primitiveLong = primitiveLong;
        }

        public Long getObjectLong() {
            return objectLong;
        }

        public void setObjectLong(Long objectLong) {
            this.objectLong = objectLong;
        }
    }

    public static class RadixPadSize5Dto {
        @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 5)
        private long primitiveLong;
        @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 5)
        private Long objectLong;

        public long getPrimitiveLong() {
            return primitiveLong;
        }

        public void setPrimitiveLong(long primitiveLong) {
            this.primitiveLong = primitiveLong;
        }

        public Long getObjectLong() {
            return objectLong;
        }

        public void setObjectLong(Long objectLong) {
            this.objectLong = objectLong;
        }
    }

    public static class FriendlyIdDto {
        @AsString(value = AsString.Type.FRIENDLY_ID)
        private long primitiveLong;
        @AsString(value = AsString.Type.FRIENDLY_ID)
        private Long objectLong;

        public long getPrimitiveLong() {
            return primitiveLong;
        }

        public void setPrimitiveLong(long primitiveLong) {
            this.primitiveLong = primitiveLong;
        }

        public Long getObjectLong() {
            return objectLong;
        }

        public void setObjectLong(Long objectLong) {
            this.objectLong = objectLong;
        }
    }
}
