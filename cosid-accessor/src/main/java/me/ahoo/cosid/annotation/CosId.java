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

import me.ahoo.cosid.provider.IdGeneratorProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields or classes for CosId.
 *
 * <p>Can be applied to:
 * <ul>
 *   <li>Fields - directly marks the field as an ID field</li>
 *   <li>Types - marks the class with a named ID field</li>
 * </ul>
 *
 * @author ahoo wang
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface CosId {
    /**
     * Default field name.
     */
    String DEFAULT_FIELD = "id";

    /**
     * Gets the ID generator name.
     *
     * @return the generator name
     */
    String value() default IdGeneratorProvider.SHARE;

    /**
     * Gets the ID field name (for type-level annotation).
     *
     * @return the field name
     */
    String field() default DEFAULT_FIELD;
}
