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

import me.ahoo.cosid.IntegerIdGenerator;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Default {@link CosIdAccessor} implementation.
 *
 * @author ahoo wang
 */
public class DefaultCosIdAccessor extends AbstractIdMetadata implements CosIdAccessor {

    private final CosIdGetter getter;
    private final CosIdSetter setter;
    private final EnsureId ensureId;

    public DefaultCosIdAccessor(IdDefinition idDefinition, CosIdGetter getter, CosIdSetter setter) {
        super(idDefinition);
        this.getter = getter;
        this.setter = setter;
        this.ensureId = getEnsureId();
    }

    private EnsureId getEnsureId() {
        Class<?> idFieldType = getIdType();
        if (Long.class.equals(idFieldType) || long.class.equals(idFieldType)) {
            return new EnsureLongId();
        }
        if (Integer.class.equals(idFieldType) || int.class.equals(idFieldType)) {
            return new EnsureIntegerId();
        }
        return new EnsureStringId();
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
        return ensureId.ensureId(target);
    }

    public class EnsureStringId implements EnsureId {

        @Override
        public boolean ensureId(Object target) {
            Object previousId = getId(target);
            if (null != previousId && !Strings.isNullOrEmpty((String) previousId)) {
                return false;
            }

            setId(target, getIdGenerator().generateAsString());
            return true;
        }
    }

    public class EnsureLongId implements EnsureId {
        private static final long MIN_ID = 0;

        @Override
        public boolean ensureId(Object target) {
            Object previousId = getId(target);
            if (null != previousId && (Long) previousId > MIN_ID) {
                return false;
            }
            setId(target, getIdGenerator().generate());
            return true;
        }
    }

    public class EnsureIntegerId implements EnsureId {
        private static final int MIN_ID = 0;
        private final IntegerIdGenerator integerIdGenerator;

        public EnsureIntegerId() {
            this.integerIdGenerator = new IntegerIdGenerator(getIdGenerator());
        }

        @Override
        public boolean ensureId(Object target) {
            Object previousId = getId(target);
            if (null != previousId && (Integer) previousId > MIN_ID) {
                return false;
            }
            setId(target, integerIdGenerator.generate());
            return true;
        }
    }
}
