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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

/**
 * @author ahoo wang
 */
public interface CosIdAccessor extends CosIdGetter, CosIdSetter, IdMetadata, EnsureId {

    NotFound NOT_FOUND = new NotFound();

    static boolean availableType(Class<?> idType) {
        return String.class.equals(idType)
            || Long.class.equals(idType)
            || long.class.equals(idType)
            || Integer.class.equals(idType)
            || int.class.equals(idType)
            ;
    }

    static void ensureAccessible(AccessibleObject accessibleObject) {
        if (!accessibleObject.isAccessible()) {
            accessibleObject.setAccessible(true);
        }
    }

    class NotFound implements CosIdAccessor {

        @Override
        public IdDefinition getIdDefinition() {
            return null;
        }

        @Override
        public IdGenerator getIdGenerator() {
            return null;
        }

        @Override
        public Field getIdField() {
            return null;
        }

        @Override
        public Object getId(Object target) {
            return null;
        }

        @Override
        public void setId(Object target, Object id) {

        }

        @Override
        public boolean ensureId(Object target) {
            return false;
        }
    }

}
