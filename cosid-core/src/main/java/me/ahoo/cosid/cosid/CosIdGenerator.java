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

package me.ahoo.cosid.cosid;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.generator.CosIdGeneratorStat;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import jakarta.annotation.Nonnull;

/**
 * CosIdGenerator algorithm ID generator.
 *
 * <p><img src="../doc-files/CosIdGenerator.png" alt="CosIdGenerator"></p>
 *
 * @author ahoo wang
 */
public interface CosIdGenerator extends IdGenerator {
    int getMachineId();
    
    long getLastTimestamp();
    
    @Nonnull
    CosIdIdStateParser getStateParser();
    
    @Nonnull
    @Override
    default IdConverter idConverter() {
        throw new UnsupportedOperationException("CosIdGenerator does not support IdConverter,please use CosIdIdStateParser instead!");
    }
    
    @Override
    default long generate() {
        throw new UnsupportedOperationException("CosIdGenerator does not support the generation of long IDs!");
    }
    
    @Nonnull
    CosIdState generateAsState();
    
    @Nonnull
    @Override
    default String generateAsString() {
        return getStateParser().asString(generateAsState());
    }
    
    @Override
    default IdGeneratorStat stat() {
        return new CosIdGeneratorStat(getClass().getSimpleName(), getMachineId(), getLastTimestamp(), Stat.simple(getStateParser().getClass().getSimpleName()));
    }
}
