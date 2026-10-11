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

import me.ahoo.cosid.IdGenerator;

import com.google.errorprone.annotations.Immutable;

import java.lang.reflect.Field;

/**
 * Metadata container for ID field information.
 *
 * <p>Provides access to ID definition, generator, and field metadata
 * for objects annotated with {@code @CosId}.
 *
 * @author ahoo wang
 */
@Immutable
public interface IdMetadata {

    /**
     * Gets the ID definition.
     *
     * @return the ID definition
     */
    IdDefinition getIdDefinition();

    /**
     * Gets the generator name from the ID definition.
     *
     * @return the generator name
     */
    default String getGeneratorName() {
        return getIdDefinition().getGeneratorName();
    }

    /**
     * Gets the ID generator.
     *
     * @return the ID generator
     */
    IdGenerator getIdGenerator();

    /**
     * Gets the ID field from the definition.
     *
     * @return the ID field
     */
    default Field getIdField() {
        return getIdDefinition().getIdField();
    }

    /**
     * Gets the declaring class of the ID field.
     *
     * @return the declaring class
     */
    default Class<?> getIdDeclaringClass() {
        return getIdField().getDeclaringClass();
    }

    /**
     * Gets the type of the ID field.
     *
     * @return the ID type
     */
    default Class<?> getIdType() {
        return getIdDefinition().getIdType();
    }
}
