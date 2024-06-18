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

import java.lang.reflect.Field;
import java.util.List;

/**
 * Composite {@link FieldDefinitionParser}.
 *
 * @see FieldDefinitionParser
 * @see NamedDefinitionParser
 */
public class CompositeFieldDefinitionParser implements FieldDefinitionParser {
    private final List<FieldDefinitionParser> fieldDefinitionParsers;
    
    public CompositeFieldDefinitionParser(List<FieldDefinitionParser> fieldDefinitionParsers) {
        this.fieldDefinitionParsers = fieldDefinitionParsers;
    }
    
    @Override
    public IdDefinition parse(List<Class<?>> lookupClassList, Field field) {
        for (FieldDefinitionParser fieldDefinitionParser : fieldDefinitionParsers) {
            IdDefinition idDefinition = fieldDefinitionParser.parse(lookupClassList, field);
            if (idDefinition != IdDefinition.NOT_FOUND) {
                return idDefinition;
            }
        }
        return IdDefinition.NOT_FOUND;
    }
}
