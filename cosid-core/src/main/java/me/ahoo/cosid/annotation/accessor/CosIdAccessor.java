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

package me.ahoo.cosid.annotation.accessor;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.annotation.CosIdDefinition;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * @author ahoo wang
 */
public interface CosIdAccessor extends CosIdGetter, CosIdSetter {

    NotFound NOT_FOUND = new NotFound();

    default boolean ensureId(Object target, IdGeneratorProvider idGeneratorProvider) {
        CosIdDefinition cosIdDefinition = getCosIdDefinition();
        Preconditions.checkArgument(getIdDeclaringClass().isInstance(target), "target:[%s] is not instance of IdDeclaringClass:[%s]", target, getIdDeclaringClass());
        IdGenerator idGenerator = idGeneratorProvider.get(cosIdDefinition.getGeneratorName())
                .orElseThrow(() -> new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] not fond.", cosIdDefinition.getGeneratorName())));

        Object previousId = get(target);

        if (String.class.equals(getIdType())) {
            if (Objects.nonNull(previousId) && !Strings.isNullOrEmpty(previousId.toString())) {
                return false;
            }

            if (!cosIdDefinition.isFriendlyId()) {
                set(target, idGenerator.generateAsString());
                return true;
            }

            Preconditions.checkState(idGenerator instanceof SnowflakeFriendlyId, "idGenerator:[%s] is not SnowflakeFriendlyId. IdType:[%s]", cosIdDefinition.getGeneratorName(), getIdType());
            String friendlyId = ((SnowflakeFriendlyId) idGenerator).friendlyId().getFriendlyId();
            set(target, friendlyId);
            return true;
        }

        if (Objects.isNull(previousId) || (Long) previousId < 1) {
            set(target, idGenerator.generate());
            return true;
        }
        return false;

    }

    static boolean availableType(Class<?> idType) {
        return String.class.equals(idType)
                || Long.class.equals(idType)
                || long.class.equals(idType);
    }

    static void ensureAccessible(AccessibleObject accessibleObject) {
        if (!accessibleObject.isAccessible()) {
            accessibleObject.setAccessible(true);
        }
    }

    class NotFound implements CosIdAccessor {

        @Override
        public CosIdDefinition getCosIdDefinition() {
            return null;
        }

        @Override
        public Field getIdField() {
            return null;
        }

        @Override
        public Object get(Object target) {
            return null;
        }

        @Override
        public void set(Object target, Object value) {

        }
    }

}
