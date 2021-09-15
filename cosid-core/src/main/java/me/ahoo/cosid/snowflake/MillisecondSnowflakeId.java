/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
 * @author ahoo wang
 * Creation time 2020/9/22 20:13
 **/
public class MillisecondSnowflakeId extends AbstractSnowflakeId {

    public final static int DEFAULT_TIMESTAMP_BIT = 41;
    public final static int DEFAULT_MACHINE_BIT = 10;
    public final static int DEFAULT_SEQUENCE_BIT = 12;

    public MillisecondSnowflakeId(long machineId) {
        this(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId);
    }

    public MillisecondSnowflakeId(int machineBit, long machineId) {
        super(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, machineBit, DEFAULT_SEQUENCE_BIT, machineId);
    }

    public MillisecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, long machineId) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    @Override
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }
}
