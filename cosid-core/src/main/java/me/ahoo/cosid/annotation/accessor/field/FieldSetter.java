/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosid.annotation.accessor.field;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.annotation.accessor.AbstractIdMetadata;
import me.ahoo.cosid.annotation.accessor.CosIdAccessor;
import me.ahoo.cosid.annotation.accessor.CosIdSetter;

import java.lang.reflect.Field;

/**
 * @author ahoo wang
 */
public class FieldSetter extends AbstractIdMetadata implements CosIdSetter {

    public FieldSetter(CosId cosId, Field field) {
        super(cosId, field);
        CosIdAccessor.ensureAccessible(field);
    }

    @Override
    public void set(Object target, Object value) {
        try {
            getIdField().set(target, value);
        } catch (IllegalAccessException e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }
}
