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

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.stat.Stat;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

/**
 * ClockSync {@link CosIdGenerator}.
 * <p>
 * If the clock is backwards, synchronize the clock and retry generating the Id.
 * </p>
 *
 * @see ClockBackwardsSynchronizer
 * @see CosIdGenerator
 * @see CosIdIdStateParser
 * @see CosIdState
 * @see Decorator
 * @see IdGeneratorStat
 * @see Stat
 * @see ClockBackwardsException
 * @see ClockBackwardsSynchronizer
 */
@Slf4j
public class ClockSyncCosIdGenerator implements CosIdGenerator, Decorator<CosIdGenerator> {
    private final CosIdGenerator actual;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    
    public ClockSyncCosIdGenerator(CosIdGenerator actual) {
        this(actual, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    public ClockSyncCosIdGenerator(CosIdGenerator actual, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.actual = actual;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }
    
    @Nonnull
    @Override
    public CosIdGenerator getActual() {
        return actual;
    }
    
    @Override
    public int getMachineId() {
        return actual.getMachineId();
    }
    
    @Override
    public long getLastTimestamp() {
        return actual.getLastTimestamp();
    }
    
    @Nonnull
    @Override
    public CosIdIdStateParser getStateParser() {
        return actual.getStateParser();
    }
    
    @Nonnull
    @Override
    public CosIdState generateAsState() {
        try {
            return actual.generateAsState();
        } catch (ClockBackwardsException exception) {
            if (log.isWarnEnabled()) {
                log.warn(exception.getMessage(), exception);
            }
            clockBackwardsSynchronizer.syncUninterruptibly(actual.getLastTimestamp());
            return actual.generateAsState();
        }
    }
    
    @Override
    public IdGeneratorStat stat() {
        return IdGeneratorStat.simple(getClass().getSimpleName(), actual.stat(), Stat.simple(getStateParser().getClass().getSimpleName()));
    }
}
