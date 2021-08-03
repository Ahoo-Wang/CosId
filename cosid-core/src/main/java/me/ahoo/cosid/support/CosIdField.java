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

package me.ahoo.cosid.support;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * @author ahoo wang
 */
public class CosIdField {
    private final CosId cosId;
    private final Field field;
    private final Class<?> fieldDeclaringClass;
    private final Class<?> fieldClass;

    public CosIdField(CosId cosId, Field field) {
        this.cosId = cosId;
        this.field = field;
        this.fieldDeclaringClass = field.getDeclaringClass();
        this.fieldClass = field.getType();

        Preconditions.checkArgument(String.class.equals(this.fieldClass)
                        || Long.class.equals(this.fieldClass)
                        || long.class.equals(this.fieldClass)
                , "fieldClass:[%s] must be String or Long.", this.fieldClass.getName());
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }
    }

    public CosId getCosId() {
        return cosId;
    }

    public Field getField() {
        return field;
    }

    public Class<?> getFieldDeclaringClass() {
        return fieldDeclaringClass;
    }

    public Class<?> getFieldClass() {
        return fieldClass;
    }

    public boolean ensureId(Object target, IdGeneratorProvider idGeneratorProvider) {
        Preconditions.checkArgument(fieldDeclaringClass.isInstance(target), "target:[%s] is not instance of fieldDeclaringClass:[%s]", target, fieldDeclaringClass);
        IdGenerator idGenerator = idGeneratorProvider.get(cosId.value()).orElseThrow(() -> new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] not fond.", cosId.value())));

        try {
            Object previousId = field.get(target);

            if (String.class.equals(fieldClass)) {
                if (Objects.nonNull(previousId) && !Strings.isNullOrEmpty(previousId.toString())) {
                    return false;
                }

                if (!cosId.friendlyId()) {
                    field.set(target, idGenerator.generateAsString());
                    return true;
                }

                Preconditions.checkState(idGenerator instanceof SnowflakeFriendlyId, "idGenerator:[%s] is not SnowflakeFriendlyId. fieldClass:[%s]", cosId.value(), fieldClass);
                String friendlyId = ((SnowflakeFriendlyId) idGenerator).friendlyId().getFriendlyId();
                field.set(target, friendlyId);
                return true;
            }

            if (Objects.isNull(previousId) || (Long) previousId < 1) {
                field.set(target, idGenerator.generate());
                return true;
            }
            return false;
        } catch (IllegalAccessException illegalAccessException) {
            throw new CosIdException(illegalAccessException.getMessage(), illegalAccessException);
        }
    }
}
