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
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.jspecify.annotations.NonNull;

/**
 * SnowflakeId that refuses to generate ids once the lease of its machine id is lost or expired.
 *
 * <p>Fail fast: a duplicate id is worse than a failed request. See {@link MachineIdLease}.
 */
public class LeasedSnowflakeId implements IdGeneratorDecorator, SnowflakeId {
    
    private final SnowflakeId actual;
    private final MachineIdLease lease;
    
    public LeasedSnowflakeId(SnowflakeId actual, MachineIdLease lease) {
        this.actual = actual;
        this.lease = lease;
    }
    
    @Override
    public @NonNull SnowflakeId getActual() {
        return actual;
    }
    
    public MachineIdLease getLease() {
        return lease;
    }
    
    @Override
    public long generate() throws MachineIdLostException {
        lease.ensureValid();
        return actual.generate();
    }
    
    @Override
    public IdGeneratorStat stat() {
        return IdGeneratorDecorator.super.stat();
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
    public int getMaxMachineId() {
        return actual.getMaxMachineId();
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
    public long getLastTimestampAsMilliseconds() {
        return actual.getLastTimestampAsMilliseconds();
    }
    
    @Override
    public int getMachineId() {
        return actual.getMachineId();
    }
}
