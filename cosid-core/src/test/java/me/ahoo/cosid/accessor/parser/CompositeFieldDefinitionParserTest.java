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

package me.ahoo.cosid.accessor.parser;

import me.ahoo.cosid.accessor.IdDefinition;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.entity.IdNotFoundEntity;
import me.ahoo.cosid.annotation.entity.LongIdEntity;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

class CompositeFieldDefinitionParserTest {
    
    @SneakyThrows
    @Test
    void parse() {
        var compositeFieldDefinitionParser = new CompositeFieldDefinitionParser(List.of(AnnotationDefinitionParser.INSTANCE));
        Field idField = LongIdEntity.class.getDeclaredField("id");
        IdDefinition idDefinition = compositeFieldDefinitionParser.parse(List.of(LongIdEntity.class), idField);
        
        Assertions.assertNotEquals(IdDefinition.NOT_FOUND, idDefinition);
        Assertions.assertEquals(IdGeneratorProvider.SHARE, idDefinition.getGeneratorName());
    }
    
    @SneakyThrows
    @Test
    void parseIfNotFound() {
        var compositeFieldDefinitionParser = new CompositeFieldDefinitionParser(List.of(AnnotationDefinitionParser.INSTANCE));
        Field nameField = IdNotFoundEntity.class.getDeclaredField("name");
        IdDefinition idDefinition = compositeFieldDefinitionParser.parse(List.of(IdNotFoundEntity.class), nameField);
        Assertions.assertEquals(IdDefinition.NOT_FOUND, idDefinition);
    }
}