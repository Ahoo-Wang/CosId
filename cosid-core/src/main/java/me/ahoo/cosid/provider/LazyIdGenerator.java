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

import com.google.common.base.Strings;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;

import java.util.Optional;

/**
 * @author ahoo wang
 */
public final class LazyIdGenerator implements IdGenerator {

    private final String idName;

    private IdGenerator lazyIdGen;

    public LazyIdGenerator(String idName) {
        this.idName = idName;
    }

    public String getIdName() {
        return idName;
    }

    public IdGenerator tryGet(boolean required) {
        if (lazyIdGen != null) {
            return lazyIdGen;
        }
        String idName = getIdName();
        Optional<IdGenerator> idGeneratorOp = DefaultIdGeneratorProvider.INSTANCE.get(idName);
        if (idGeneratorOp.isPresent()) {
            lazyIdGen = idGeneratorOp.get();
            return lazyIdGen;
        } else if (required) {
            throw new CosIdException(Strings.lenientFormat("CosId:[%s] not found!", idName));
        }
        return null;
    }

    public SnowflakeId asSnowflakeId(boolean required) {
        IdGenerator idGenerator = tryGet(required);
        if (idGenerator instanceof SnowflakeId) {
            return (SnowflakeId) idGenerator;
        }
        throw new CosIdException(Strings.lenientFormat("CosId:[%s] is not instanceof SnowflakeId!", idName));
    }

    public SnowflakeFriendlyId asFriendlyId(boolean required) {
        IdGenerator idGenerator = tryGet(required);
        if (idGenerator instanceof SnowflakeFriendlyId) {
            return (SnowflakeFriendlyId) idGenerator;
        }
        throw new CosIdException(Strings.lenientFormat("CosId:[%s] is not instanceof SnowflakeFriendlyId!", idName));
    }

    public SegmentId asSegmentId(boolean required) {
        IdGenerator idGenerator = tryGet(required);
        if (idGenerator instanceof SegmentId) {
            return (SegmentId) idGenerator;
        }
        throw new CosIdException(Strings.lenientFormat("CosId:[%s] is not instanceof SegmentId!", idName));
    }

    @Override
    public long generate() {
        return tryGet(true).generate();
    }

    @Override
    public IdConverter idConverter() {
        return tryGet(true).idConverter();
    }
}
