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

package me.ahoo.cosid.provider;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IdGeneratorDecorator;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;

import com.google.common.base.Strings;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Lazy loading IdGenerator.
 *
 * <p>Delays the lookup of an ID generator from the provider until first access.
 * This is useful when the generator might not be immediately available at startup.
 *
 * @author ahoo wang
 */
public final class LazyIdGenerator implements IdGeneratorDecorator {

    private final String generatorName;

    private volatile IdGenerator lazyIdGen;

    private final IdGeneratorProvider idGeneratorProvider;

    /**
     * Creates a lazy generator with default provider.
     *
     * @param generatorName the name of the generator to lookup
     */
    public LazyIdGenerator(String generatorName) {
        this(generatorName, DefaultIdGeneratorProvider.INSTANCE);
    }

    /**
     * Creates a lazy generator with custom provider.
     *
     * @param generatorName the name of the generator to lookup
     * @param idGeneratorProvider the provider to use for lookup
     */
    public LazyIdGenerator(String generatorName, IdGeneratorProvider idGeneratorProvider) {
        this.generatorName = generatorName;
        this.idGeneratorProvider = idGeneratorProvider;
    }

    /**
     * Gets the generator name.
     *
     * @return the generator name
     */
    public String getGeneratorName() {
        return generatorName;
    }

    /**
     * Attempts to get the generator, optionally throwing if not found.
     *
     * @param required if true, throws NotFoundIdGeneratorException if not found
     * @return the generator or null if not required and not found
     * @throws NotFoundIdGeneratorException if required and not found
     */
    public IdGenerator tryGet(boolean required) {
        if (null != lazyIdGen) {
            return lazyIdGen;
        }
        String generatorName = getGeneratorName();
        Optional<IdGenerator> idGeneratorOp = idGeneratorProvider.get(generatorName);
        if (idGeneratorOp.isPresent()) {
            lazyIdGen = idGeneratorOp.get();
            return lazyIdGen;
        } else if (required) {
            throw new NotFoundIdGeneratorException(generatorName);
        }
        return null;
    }

    /**
     * Gets this generator as a SnowflakeId.
     *
     * @param required if true, throws if not a SnowflakeId
     * @return the SnowflakeId or null
     * @throws CosIdException if not a SnowflakeId when required
     */
    public SnowflakeId asSnowflakeId(boolean required) {
        IdGenerator idGenerator = tryGet(required);
        if (null == idGenerator) {
            return null;
        }
        if (idGenerator instanceof SnowflakeId) {
            return (SnowflakeId) idGenerator;
        }
        throw new CosIdException(Strings.lenientFormat("IdGenerator:[%s] is not instanceof SnowflakeId!", generatorName));
    }

    /**
     * Gets this generator as a SnowflakeFriendlyId.
     *
     * @param required if true, throws if not a SnowflakeFriendlyId
     * @return the SnowflakeFriendlyId or null
     * @throws CosIdException if not a SnowflakeFriendlyId when required
     */
    public SnowflakeFriendlyId asFriendlyId(boolean required) {
        IdGenerator idGenerator = tryGet(required);
        if (null == idGenerator) {
            return null;
        }
        if (idGenerator instanceof SnowflakeFriendlyId) {
            return (SnowflakeFriendlyId) idGenerator;
        }
        throw new CosIdException(Strings.lenientFormat("IdGenerator:[%s] is not instanceof SnowflakeFriendlyId!", generatorName));
    }

    /**
     * Gets this generator as a SegmentId.
     *
     * @param required if true, throws if not a SegmentId
     * @return the SegmentId or null
     * @throws CosIdException if not a SegmentId when required
     */
    public SegmentId asSegmentId(boolean required) {
        IdGenerator idGenerator = tryGet(required);
        if (null == idGenerator) {
            return null;
        }
        if (idGenerator instanceof SegmentId) {
            return (SegmentId) idGenerator;
        }
        throw new CosIdException(Strings.lenientFormat("IdGenerator:[%s] is not instanceof SegmentId!", generatorName));
    }

    @Override
    public @NonNull IdGenerator getActual() {
        return tryGet(true);
    }

    @Override
    public @NonNull IdConverter idConverter() {
        return getActual().idConverter();
    }
}
