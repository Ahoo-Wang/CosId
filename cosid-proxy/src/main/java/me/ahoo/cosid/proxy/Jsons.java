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

package me.ahoo.cosid.proxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public final class Jsons {
    private Jsons() {
    }
    
    public static final ObjectMapper OBJECT_MAPPER = mapper();
    
    public static ObjectMapper mapper() {
        return new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .configure(JsonParser.Feature.IGNORE_UNDEFINED, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @SneakyThrows
    public static String serialize(Object serializeObject) {
        return OBJECT_MAPPER.writeValueAsString(serializeObject);
    }
    
    @SneakyThrows
    public static <T> T deserialize(String content, Class<T> deserializeType) {
        return OBJECT_MAPPER.readValue(content, deserializeType);
    }
}
