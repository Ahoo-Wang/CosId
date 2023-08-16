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

package me.ahoo.cosid.stat;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.snowflake.SnowflakeId;

import me.ahoo.cosid.stat.generator.SegmentIdStat;

import java.util.Objects;

@FunctionalInterface
public interface Statistical {
    Stat stat();
    
    @SuppressWarnings("checkstyle:EmptyCatchBlock")
    static Stat stat(IdGenerator idGenerator) {
        Objects.requireNonNull(idGenerator, "statistical");
        if (idGenerator instanceof Statistical statistical) {
            return statistical.stat();
        }
        
        var kind = Decorator.chain(idGenerator);
        var converterKind = "UnsupportedOperation";
        try {
            converterKind = Decorator.chain(idGenerator.idConverter());
        } catch (Throwable ignore) {
            //ignore
        }
        if (idGenerator instanceof CosIdGenerator cosidGenerator) {
            return new CosIdGeneratorStat(kind,
                converterKind,
                cosidGenerator.getMachineId(),
                cosidGenerator.getLastTimestamp()
            );
        }
        if (idGenerator instanceof SnowflakeId snowflakeId) {
            return new SnowflakeIdStat(kind,
                converterKind,
                snowflakeId.getEpoch(),
                snowflakeId.getTimestampBit(),
                snowflakeId.getMachineBit(),
                snowflakeId.getSequenceBit(),
                snowflakeId.isSafeJavascript(),
                snowflakeId.getMachineId(),
                snowflakeId.getLastTimestamp()
            );
        }
        if (idGenerator instanceof SegmentId segmentId) {
            var current = segmentId.current();
            return new SegmentIdStat(
                kind,
                converterKind,
                current.getFetchTime(),
                current.getMaxId(),
                current.getOffset(),
                current.getSequence(),
                current.getStep(),
                current.isExpired(),
                current.isOverflow(),
                current.isAvailable()
            );
        }
        
        return Stat.simple(kind);
    }
}
