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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Objects;

/**
 * @author ahoo wang
 */
public class DefaultCosIdAccessor extends AbstractIdMetadata implements CosIdAccessor {

    private final CosIdGetter getter;
    private final CosIdSetter setter;

    public DefaultCosIdAccessor(IdDefinition idDefinition, CosIdGetter getter, CosIdSetter setter) {
        super(idDefinition);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public Object getId(Object target) {
        return getter.getId(target);
    }

    @Override
    public void setId(Object target, Object id) {
        setter.setId(target, id);
    }

    public CosIdGetter getGetter() {
        return getter;
    }

    public CosIdSetter getSetter() {
        return setter;
    }

    @Override
    public boolean ensureId(Object target) {

        Preconditions.checkArgument(getIdDeclaringClass().isInstance(target), "target:[%s] is not instance of IdDeclaringClass:[%s]", target, getIdDeclaringClass());

        IdGenerator idGenerator = getIdGenerator();

        Object previousId = getId(target);

        if (String.class.equals(getIdType())) {
            if (Objects.nonNull(previousId) && !Strings.isNullOrEmpty(previousId.toString())) {
                return false;
            }

            setId(target, idGenerator.generateAsString());
            return true;
        }

        if (Objects.isNull(previousId) || (Long) previousId < 1) {
            setId(target, idGenerator.generate());
            return true;
        }
        return false;
    }
}
