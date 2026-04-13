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

package me.ahoo.cosid.accessor.registry;

import me.ahoo.cosid.accessor.CosIdAccessor;

import com.google.errorprone.annotations.ThreadSafe;

/**
 * Registry for managing {@link CosIdAccessor} instances.
 *
 * <p>Provides registration and lookup of ID accessors for classes,
 * enabling automatic ID injection for entities.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface CosIdAccessorRegistry {

    /**
     * Registers a class, parsing its accessor from annotations.
     *
     * @param clazz the class to register
     */
    void register(Class<?> clazz);

    /**
     * Registers a class with a specific accessor.
     *
     * @param clazz the class to register
     * @param cosIdAccessor the accessor to use
     */
    void register(Class<?> clazz, CosIdAccessor cosIdAccessor);

    /**
     * Gets the accessor for a class.
     *
     * @param clazz the class
     * @return the accessor
     */
    CosIdAccessor get(Class<?> clazz);

    /**
     * Ensures the target object has an ID, registering if needed.
     *
     * @param target the target object
     * @return true if ID was ensured
     */
    default boolean ensureId(Object target) {
        CosIdAccessor cosIdAccessor = get(target.getClass());
        if (CosIdAccessor.NOT_FOUND.equals(cosIdAccessor)) {
            return false;
        }
        return cosIdAccessor.ensureId(target);
    }
}
