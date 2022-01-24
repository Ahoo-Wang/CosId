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

package me.ahoo.cosid.annotation;

import me.ahoo.cosid.accessor.IdDefinition;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * @author ahoo wang
 */
@Slf4j
public class AnnotationDefinitionParser implements FieldDefinitionParser {

    public static final AnnotationDefinitionParser INSTANCE = new AnnotationDefinitionParser();

    @Override
    public IdDefinition parse(Class<?> clazz, Field field) {

        CosId clazzCosId = clazz.getAnnotation(CosId.class) != null
            ? clazz.getAnnotation(CosId.class) : field.getDeclaringClass().getAnnotation(CosId.class);

        if (null != clazzCosId) {
            if (!field.getName().equals(clazzCosId.field())) {
                return IdDefinition.NOT_FOUND;
            }
            return new IdDefinition(clazzCosId.value(), field);
        }

        CosId fieldCosId = field.getAnnotation(CosId.class);
        if (null == fieldCosId) {
            return IdDefinition.NOT_FOUND;
        }
        return new IdDefinition(fieldCosId.value(), field);
    }
}
