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

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.StringIdGenerator;

/**
 * String SnowflakeId.
 *
 * @author ahoo wang
 */
public class StringSnowflakeId extends StringIdGenerator implements SnowflakeId {
    private final SnowflakeId snowflakeId;

    public StringSnowflakeId(SnowflakeId actual, IdConverter idConverter) {
        super(actual, idConverter);
        this.snowflakeId = actual;
    }

    @Override
    public long getEpoch() {
        return snowflakeId.getEpoch();
    }

    @Override
    public int getTimestampBit() {
        return snowflakeId.getTimestampBit();
    }

    @Override
    public int getMachineBit() {
        return snowflakeId.getMachineBit();
    }

    @Override
    public int getSequenceBit() {
        return snowflakeId.getSequenceBit();
    }

    @Override
    public long getMaxTimestamp() {
        return snowflakeId.getMaxTimestamp();
    }

    @Override
    public long getMaxMachine() {
        return snowflakeId.getMaxMachine();
    }

    @Override
    public long getMaxSequence() {
        return snowflakeId.getMaxSequence();
    }

    @Override
    public long getLastTimestamp() {
        return snowflakeId.getLastTimestamp();
    }

    @Override
    public long getMachineId() {
        return snowflakeId.getMachineId();
    }
}
