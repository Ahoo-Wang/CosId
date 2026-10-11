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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.provider.LazyIdGenerator;

import java.lang.reflect.Field;


/**
 * Abstract base implementation of {@link IdMetadata}.
 *
 * <p>Provides common functionality for ID metadata implementations,
 * including lazy generator initialization.
 *
 * @author ahoo wang
 */
public abstract class AbstractIdMetadata implements IdMetadata {

    private final IdDefinition idDefinition;
    private final LazyIdGenerator idGenerator;

    /**
     * Creates an instance with the given ID definition, resolving generators from
     * {@link DefaultIdGeneratorProvider#INSTANCE}.
     *
     * @param idDefinition the ID definition
     */
    public AbstractIdMetadata(IdDefinition idDefinition) {
        this(idDefinition, DefaultIdGeneratorProvider.INSTANCE);
    }

    /**
     * Creates an instance with the given ID definition and provider.
     *
     * @param idDefinition        the ID definition
     * @param idGeneratorProvider the provider used to resolve the generator
     */
    public AbstractIdMetadata(IdDefinition idDefinition, IdGeneratorProvider idGeneratorProvider) {
        this.idDefinition = idDefinition;
        this.idGenerator = new LazyIdGenerator(idDefinition.getGeneratorName(), idGeneratorProvider);
    }

    @Override
    public IdDefinition getIdDefinition() {
        return idDefinition;
    }

    @Override
    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    /**
     * Checks that the ID generator, if it is already registered (or, with {@code required}, always), can generate
     * the type of the id field: a {@code long} or {@code int} field needs an {@link IdGenerator}, not a generator of
     * string ids only.
     *
     * @param required whether a missing generator fails too
     * @throws CosIdException naming the field and the generator if they do not fit
     */
    protected void checkGeneratorSupportsIdType(boolean required) {
        if (String.class.equals(getIdType())) {
            return;
        }
        StringIdGenerator generator = idGenerator.tryGet(required);
        if (generator != null && !(generator instanceof IdGenerator)) {
            Field idField = getIdField();
            throw new CosIdException("@CosId field [" + idField.getDeclaringClass().getName() + "." + idField.getName() + "] is a ["
                + getIdType().getSimpleName() + "], but ID generator [" + getGeneratorName() + "] (" + generator.getClass().getSimpleName()
                + ") generates string ids only: use a String field or an IdGenerator.");
        }
    }

}
