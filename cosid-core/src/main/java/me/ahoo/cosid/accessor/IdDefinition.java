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

package me.ahoo.cosid.accessor;

import me.ahoo.cosid.provider.IdGeneratorProvider;

import java.lang.reflect.Field;

/**
 * ID Definition.
 *
 * @author ahoo wang
 */
public class IdDefinition {
    
    public static final IdDefinition NOT_FOUND = new IdDefinition(NotFoundIdDefinition.NAME, NotFoundIdDefinition.ID_FIELD, long.class);
    
    private final String generatorName;
    private final Field idField;
    private final Class<?> idType;
    
    public IdDefinition(Field idField) {
        this(IdGeneratorProvider.SHARE, idField);
    }
    
    public IdDefinition(String generatorName, Field idField) {
        this(generatorName, idField, idField.getType());
    }
    
    public IdDefinition(String generatorName, Field idField, Class<?> idType) {
        this.generatorName = generatorName;
        this.idField = idField;
        this.idType = idType;
    }
    
    public String getGeneratorName() {
        return generatorName;
    }
    
    public Field getIdField() {
        return idField;
    }
    
    public Class<?> getIdType() {
        return idType;
    }
    
    private static class NotFoundIdDefinition {
        static final String NAME = "NOT_FOUND";
        static final Field ID_FIELD;
        
        static {
            try {
                ID_FIELD = NotFoundIdDefinition.class.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        
        private final long id = 0L;
    }
}
