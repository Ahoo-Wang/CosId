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

package me.ahoo.cosid.uncertainty;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IdGeneratorDecorator;
import me.ahoo.cosid.snowflake.SnowflakeId;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Uncertainty ID Generator.
 * For the following usage scenarios:
 * <pre>
 *     1. The problem of uneven sharding of snowflake IDs.
 *     2. I don’t want the generated ID to be predictable, such as preventing crawler by ID number, predicting transaction volume.
 * </pre>
 */
@Beta
public class UncertaintyIdGenerator implements IdGeneratorDecorator {
    protected final IdGenerator actual;
    private final int uncertaintyBits;
    private final int originalIdBits;
    private final long uncertaintyBound;
    private final long maxOriginalId;
    
    public UncertaintyIdGenerator(IdGenerator actual, int uncertaintyBits) {
        Preconditions.checkArgument(uncertaintyBits > 0 && uncertaintyBits < SnowflakeId.TOTAL_BIT, "uncertaintyBits[%s] must be greater than 0 and less than 63.");
        this.actual = actual;
        this.uncertaintyBits = uncertaintyBits;
        this.originalIdBits = SnowflakeId.TOTAL_BIT - uncertaintyBits;
        this.maxOriginalId = ~(-1L << originalIdBits);
        this.uncertaintyBound = ~(-1L << uncertaintyBits) + 1;
    }
    
    public int uncertaintyBits() {
        return uncertaintyBits;
    }
    
    public int originalIdBits() {
        return originalIdBits;
    }
    
    public long uncertaintyBound() {
        return uncertaintyBound;
    }
    
    public long maxOriginalId() {
        return maxOriginalId;
    }
    
    private long uncertainty() {
        return ThreadLocalRandom.current().nextLong(0, uncertaintyBound);
    }
    
    @Override
    public @NonNull IdGenerator getActual() {
        return actual;
    }
    
    @Override
    public long generate() {
        long originalId = getActual().generate();
        if (originalId > maxOriginalId) {
            throw new OriginalIdOverflowException(originalId, originalIdBits, maxOriginalId);
        }
        return originalId << uncertaintyBits | uncertainty();
    }

}
