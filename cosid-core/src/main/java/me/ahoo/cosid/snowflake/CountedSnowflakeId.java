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

package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.IdGeneratorDecorator;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.atomic.LongAdder;

/**
 * Counts the ids a {@link SnowflakeId} generates, for metrics. One {@link LongAdder#increment()} per id.
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public class CountedSnowflakeId implements IdGeneratorDecorator, SnowflakeId {
    
    private final SnowflakeId actual;
    private final LongAdder generated = new LongAdder();
    
    public CountedSnowflakeId(SnowflakeId actual) {
        this.actual = actual;
    }
    
    @Override
    public @NonNull SnowflakeId getActual() {
        return actual;
    }
    
    /**
     * Ids generated so far.
     *
     * @return the count
     */
    public long getGenerated() {
        return generated.sum();
    }
    
    @Override
    public long generate() {
        long id = actual.generate();
        generated.increment();
        return id;
    }
    
    @Override
    public IdGeneratorStat stat() {
        return IdGeneratorDecorator.super.stat();
    }
    
    @Override
    public SnowflakeLayout layout() {
        return actual.layout();
    }
    
    @Override
    public long getLastTimestamp() {
        return actual.getLastTimestamp();
    }
    
    @Override
    public long getLastTimestampAsMilliseconds() {
        return actual.getLastTimestampAsMilliseconds();
    }
    
    @Override
    public int getMachineId() {
        return actual.getMachineId();
    }
}
