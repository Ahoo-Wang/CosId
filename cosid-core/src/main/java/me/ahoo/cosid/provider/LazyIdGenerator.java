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

import java.util.Optional;

/**
 * Lazy loading IdGenerator.
 *
 * @author ahoo wang
 */
public final class LazyIdGenerator implements IdGeneratorDecorator {
    
    private final String generatorName;
    
    private IdGenerator lazyIdGen;
    
    private final IdGeneratorProvider idGeneratorProvider;
    
    public LazyIdGenerator(String generatorName) {
        this(generatorName, DefaultIdGeneratorProvider.INSTANCE);
    }
    
    public LazyIdGenerator(String generatorName, IdGeneratorProvider idGeneratorProvider) {
        this.generatorName = generatorName;
        this.idGeneratorProvider = idGeneratorProvider;
    }
    
    public String getGeneratorName() {
        return generatorName;
    }
    
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
    public IdGenerator getActual() {
        return tryGet(true);
    }
    
    @Override
    public IdConverter idConverter() {
        return getActual().idConverter();
    }
}
