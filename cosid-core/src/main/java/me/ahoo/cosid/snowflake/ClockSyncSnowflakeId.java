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
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;

/**
 * Clock Sync SnowflakeId.
 *
 * @author ahoo wang
 */
@Slf4j
public class ClockSyncSnowflakeId implements SnowflakeId, IdGeneratorDecorator {
    
    private final SnowflakeId actual;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    
    public ClockSyncSnowflakeId(SnowflakeId actual) {
        this(actual, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    public ClockSyncSnowflakeId(SnowflakeId actual, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.actual = actual;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }
    
    @Nonnull
    @Override
    public SnowflakeId getActual() {
        return actual;
    }
    
    @Override
    public long generate() {
        try {
            return actual.generate();
        } catch (ClockBackwardsException exception) {
            if (log.isWarnEnabled()) {
                log.warn(exception.getMessage(), exception);
            }
            clockBackwardsSynchronizer.syncUninterruptibly(actual.getLastTimestamp());
            return actual.generate();
        }
    }
    
    
    @Override
    public long getEpoch() {
        return actual.getEpoch();
    }
    
    @Override
    public int getTimestampBit() {
        return actual.getTimestampBit();
    }
    
    @Override
    public int getMachineBit() {
        return actual.getMachineBit();
    }
    
    @Override
    public int getSequenceBit() {
        return actual.getSequenceBit();
    }
    
    @Override
    public boolean isSafeJavascript() {
        return actual.isSafeJavascript();
    }
    
    @Override
    public long getMaxTimestamp() {
        return actual.getMaxTimestamp();
    }
    
    @Override
    public int getMaxMachine() {
        return actual.getMaxMachine();
    }
    
    @Override
    public long getMaxSequence() {
        return actual.getMaxSequence();
    }
    
    @Override
    public long getLastTimestamp() {
        return actual.getLastTimestamp();
    }
    
    @Override
    public int getMachineId() {
        return actual.getMachineId();
    }
    
    
}
