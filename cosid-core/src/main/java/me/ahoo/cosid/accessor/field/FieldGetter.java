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

package me.ahoo.cosid.accessor.field;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.accessor.CosIdAccessor;
import me.ahoo.cosid.accessor.CosIdGetter;

import java.lang.reflect.Field;

/**
 * @author ahoo wang
 */
public class FieldGetter implements CosIdGetter {
    private final Field idField;

    public FieldGetter(Field idField) {
        CosIdAccessor.ensureAccessible(idField);
        this.idField = idField;
    }

    @Override
    public Object getId(Object target) {
        try {
            return idField.get(target);
        } catch (IllegalAccessException e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }
}
