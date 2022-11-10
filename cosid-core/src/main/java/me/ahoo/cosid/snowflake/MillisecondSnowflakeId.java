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

import me.ahoo.cosid.CosId;

/**
 * Millisecond SnowflakeId.
 *
 * @author ahoo wang
 **/
public class MillisecondSnowflakeId extends AbstractSnowflakeId {
    
    public static final int DEFAULT_TIMESTAMP_BIT = 41;
    public static final int DEFAULT_MACHINE_BIT = 10;
    public static final int DEFAULT_SEQUENCE_BIT = 12;
    public static final long DEFAULT_SEQUENCE_RESET_THRESHOLD = ~(-1L << (DEFAULT_SEQUENCE_BIT - 1));
    
    
    public MillisecondSnowflakeId(int machineId) {
        this(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }
    
    public MillisecondSnowflakeId(int machineBit, int machineId) {
        super(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, machineBit, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }
    
    public MillisecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId, SnowflakeId.defaultSequenceResetThreshold(sequenceBit));
    }
    
    public MillisecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId, sequenceResetThreshold);
    }
    
    @Override
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }
}
