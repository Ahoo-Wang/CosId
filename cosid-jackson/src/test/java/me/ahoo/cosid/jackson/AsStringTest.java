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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.jackson.dto.CustomizeFriendlyIdDto;
import me.ahoo.cosid.jackson.dto.FriendlyIdDto;
import me.ahoo.cosid.jackson.dto.RadixDto;
import me.ahoo.cosid.jackson.dto.RadixNonePadDto;
import me.ahoo.cosid.jackson.dto.RadixPadSize5Dto;
import me.ahoo.cosid.jackson.dto.ToStringDto;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;

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
        dto.setPrimitiveLong(100);
        dto.setObjectLong(200L);
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"100\",\"objectLong\":\"200\"}", deStr);
        ToStringDto deDto = objectMapper.readValue(deStr, ToStringDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void serializeToStringWhenNull() {
        ToStringDto dto = new ToStringDto();
        dto.setPrimitiveLong(100);
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"100\",\"objectLong\":null}", deStr);
        ToStringDto deDto = objectMapper.readValue(deStr, ToStringDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void deserializeToString() {
        ToStringDto dto = new ToStringDto();
        dto.setPrimitiveLong(100);
        dto.setObjectLong(200L);
        String deStr = objectMapper.writeValueAsString(dto);
        ToStringDto deDto = objectMapper.readValue(deStr, ToStringDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void serializeRadix() {
        RadixDto dto = new RadixDto();
        dto.setPrimitiveLong(100);
        dto.setObjectLong(200L);
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"0000000001c\",\"objectLong\":\"0000000003E\"}", deStr);
        RadixDto deDto = objectMapper.readValue(deStr, RadixDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void deserializeToRadix() {
        RadixDto dto = new RadixDto();
        dto.setPrimitiveLong(100);
        dto.setObjectLong(200L);
        String deStr = objectMapper.writeValueAsString(dto);
        RadixDto deDto = objectMapper.readValue(deStr, RadixDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void serializeRadixNonePad() {
        RadixNonePadDto dto = new RadixNonePadDto();
        dto.setPrimitiveLong(100);
        dto.setObjectLong(200L);
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"1c\",\"objectLong\":\"3E\"}", deStr);
        RadixNonePadDto deDto = objectMapper.readValue(deStr, RadixNonePadDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void serializeRadixPadSize5() {
        RadixPadSize5Dto dto = new RadixPadSize5Dto();
        dto.setPrimitiveLong(100);
        dto.setObjectLong(200L);
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"0001c\",\"objectLong\":\"0003E\"}", deStr);
        RadixPadSize5Dto deDto = objectMapper.readValue(deStr, RadixPadSize5Dto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    
    @SneakyThrows
    @Test
    public void serializeFriendlyId() {
        FriendlyIdDto dto = new FriendlyIdDto();
        dto.setPrimitiveLong(266231902451535872L);
        dto.setObjectLong(266231902451535873L);
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\""
            + MillisecondSnowflakeIdStateParser.INSTANCE.parse(dto.getPrimitiveLong()).getFriendlyId()
            + "\",\"objectLong\":\"" + MillisecondSnowflakeIdStateParser.INSTANCE.parse(dto.getObjectLong()).getFriendlyId()
            + "\"}", deStr);
        FriendlyIdDto deDto = objectMapper.readValue(deStr, FriendlyIdDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void serializeCustomizeFriendlyId() {
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(2, 2);
        SnowflakeFriendlyId snowflakeFriendlyId = new DefaultSnowflakeFriendlyId(snowflakeId);
        CustomizeFriendlyIdDto dto = new CustomizeFriendlyIdDto();
        dto.setPrimitiveLong(snowflakeFriendlyId.generate());
        dto.setObjectLong(snowflakeFriendlyId.generate());
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\""
            + snowflakeFriendlyId.getParser().parse(dto.getPrimitiveLong()).getFriendlyId()
            + "\",\"objectLong\":\"" + snowflakeFriendlyId.getParser().parse(dto.getObjectLong()).getFriendlyId()
            + "\"}", deStr);
        CustomizeFriendlyIdDto deDto = objectMapper.readValue(deStr, CustomizeFriendlyIdDto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.getPrimitiveLong(), deDto.getPrimitiveLong());
        Assertions.assertEquals(dto.getObjectLong(), deDto.getObjectLong());
    }
    
    @SneakyThrows
    @Test
    public void testNull() {
        ToStringDto deDto = objectMapper.readValue("{\"primitiveLong\":\"1\",\"objectLong\":\"2\"}", ToStringDto.class);
        Assertions.assertNotNull(deDto);
        
    }
    
    
}
