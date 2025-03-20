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
 * CosIdAccessor Registry.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface CosIdAccessorRegistry {

    void register(Class<?> clazz);

    void register(Class<?> clazz, CosIdAccessor cosIdAccessor);

    CosIdAccessor get(Class<?> clazz);

    default boolean ensureId(Object target) {
        CosIdAccessor cosIdAccessor = get(target.getClass());
        if (CosIdAccessor.NOT_FOUND.equals(cosIdAccessor)) {
            return false;
        }
        return cosIdAccessor.ensureId(target);
    }
}
