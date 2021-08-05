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

package me.ahoo.cosid.annotation.accessor;

import me.ahoo.cosid.annotation.CosId;

import java.lang.reflect.Field;

/**
 * @author ahoo wang
 */
public class DefaultCosIdAccessor implements CosIdAccessor {

    private final CosIdGetter getter;
    private final CosIdSetter setter;

    public DefaultCosIdAccessor(CosIdGetter getter, CosIdSetter setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public CosId getCosId() {
        return getter.getCosId();
    }

    @Override
    public Field getIdField() {
        return getter.getIdField();
    }

    @Override
    public Class<?> getIdDeclaringClass() {
        return getter.getIdDeclaringClass();
    }

    @Override
    public Object get(Object target) {
        return getter.get(target);
    }

    @Override
    public void set(Object target, Object value) {
        setter.set(target, value);
    }

    @Override
    public Class<?> getIdType() {
        return getter.getIdType();
    }

    public CosIdGetter getGetter() {
        return getter;
    }

    public CosIdSetter getSetter() {
        return setter;
    }
}
