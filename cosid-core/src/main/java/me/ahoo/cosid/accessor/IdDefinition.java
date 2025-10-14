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
 * ID definition that describes how an ID field should be handled.
 * 
 * <p>This class encapsulates metadata about an ID field in an object, including:
 * <ul>
 *   <li>The name of the ID generator to use</li>
 *   <li>The field that should receive the generated ID</li>
 *   <li>The type of the ID field (String, Long, Integer, etc.)</li>
 * </ul>
 * 
 * <p>The ID definition is used by the CosId accessor framework to automatically
 * inject generated IDs into objects when they are processed. This enables
 * automatic ID assignment without requiring explicit code in the application.
 *
 * @author ahoo wang
 */
public class IdDefinition {
    
    /**
     * A sentinel value representing a "not found" ID definition.
     * 
     * <p>This is used when no valid ID field can be found in an object, allowing
     * the framework to handle missing ID fields gracefully without null checks.
     */
    public static final IdDefinition NOT_FOUND = new IdDefinition(NotFoundIdDefinition.NAME, NotFoundIdDefinition.ID_FIELD, long.class);
    
    /**
     * The name of the ID generator to use for this field.
     * 
     * <p>This name is used to look up the appropriate generator from the
     * {@link IdGeneratorProvider}. If not specified, the shared generator
     * is typically used.
     */
    private final String generatorName;
    
    /**
     * The field that should receive the generated ID.
     * 
     * <p>This is the actual {@link Field} object that will be populated
     * with the generated ID value during object processing.
     */
    private final Field idField;
    
    /**
     * The type of the ID field.
     * 
     * <p>This specifies the data type of the ID field (String, Long, Integer, etc.)
     * which determines how the generated ID value will be converted before assignment.
     */
    private final Class<?> idType;
    
    /**
     * Create a new ID definition with the default shared generator.
     * 
     * <p>This constructor creates an ID definition that uses the shared ID generator
     * ({@link IdGeneratorProvider#SHARE}) to generate IDs for the specified field.
     *
     * @param idField The field that should receive the generated ID
     */
    public IdDefinition(Field idField) {
        this(IdGeneratorProvider.SHARE, idField);
    }
    
    /**
     * Create a new ID definition with the specified generator name.
     * 
     * <p>This constructor creates an ID definition that uses the named ID generator
     * to generate IDs for the specified field. The field's type is inferred from
     * the field itself.
     *
     * @param generatorName The name of the ID generator to use
     * @param idField The field that should receive the generated ID
     */
    public IdDefinition(String generatorName, Field idField) {
        this(generatorName, idField, idField.getType());
    }
    
    /**
     * Create a new ID definition with all parameters specified.
     * 
     * <p>This constructor creates an ID definition with explicit control over
     * all aspects: generator name, target field, and field type.
     *
     * @param generatorName The name of the ID generator to use
     * @param idField The field that should receive the generated ID
     * @param idType The type of the ID field
     */
    public IdDefinition(String generatorName, Field idField, Class<?> idType) {
        this.generatorName = generatorName;
        this.idField = idField;
        this.idType = idType;
    }
    
    /**
     * Get the name of the ID generator to use for this field.
     * 
     * @return The name of the ID generator
     */
    public String getGeneratorName() {
        return generatorName;
    }
    
    /**
     * Get the field that should receive the generated ID.
     * 
     * @return The field that should receive the generated ID
     */
    public Field getIdField() {
        return idField;
    }
    
    /**
     * Get the type of the ID field.
     * 
     * @return The type of the ID field
     */
    public Class<?> getIdType() {
        return idType;
    }
    
    /**
     * Internal class used to create the NOT_FOUND sentinel value.
     * 
     * <p>This class exists solely to provide a valid Field object for the
     * NOT_FOUND sentinel, avoiding null pointer exceptions in code that
     * assumes an ID definition always has a valid field.
     */
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
