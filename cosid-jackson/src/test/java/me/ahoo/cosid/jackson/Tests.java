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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class Tests {
    ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    public void test() {
        Dto dto = new Dto();
        dto.primitiveLong = 1;
        dto.objectLong = 2L;
        String deStr = objectMapper.writeValueAsString(dto);
        Assertions.assertEquals("{\"primitiveLong\":\"1\",\"objectLong\":\"2\"}", deStr);
        Dto deDto = objectMapper.readValue(deStr, Dto.class);
        Assertions.assertNotNull(deDto);
        Assertions.assertEquals(dto.primitiveLong,deDto.primitiveLong);
        Assertions.assertEquals(dto.objectLong,deDto.objectLong);
    }


    @SneakyThrows
    @Test
    public void testNull() {

        Dto deDto = objectMapper.readValue("{\"primitiveLong\":\"1\",\"objectLong\":\"2\"}", Dto.class);
        Assertions.assertNotNull(deDto);

    }

    public static class Dto {
        @JsonSerialize(using = StringIdJsonSerializer.class)
        @JsonDeserialize(using = StringIdJsonDeserializer.class)
        private long primitiveLong;
        @JsonSerialize(using = StringIdJsonSerializer.class)
        @JsonDeserialize(using = StringIdJsonDeserializer.class)
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
