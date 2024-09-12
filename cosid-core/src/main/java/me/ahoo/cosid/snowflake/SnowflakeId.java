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

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;
import me.ahoo.cosid.stat.generator.SnowflakeIdStat;

/**
 * Snowflake algorithm ID generator.
 *
 * <p><img src="../doc-files/SnowflakeId.png" alt="SnowflakeId"></p>
 *
 * @author ahoo wang
 */
public interface SnowflakeId extends IdGenerator {
    int TOTAL_BIT = 63;

    long getEpoch();

    int getTimestampBit();

    int getMachineBit();

    int getSequenceBit();

    /**
     * 是否是 Javascript  安全的 SnowflakeId.
     * {@link SafeJavaScriptSnowflakeId#JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT}.
     *
     * @return Is it a JavaScript secure snowflakeId
     */
    default boolean isSafeJavascript() {
        return (getTimestampBit() + getMachineBit() + getSequenceBit()) <= SafeJavaScriptSnowflakeId.JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT;
    }

    long getMaxTimestamp();

    int getMaxMachineId();

    long getMaxSequence();

    long getLastTimestamp();

    int getMachineId();

    static long defaultSequenceResetThreshold(int sequenceBit) {
        return ~(-1L << (sequenceBit - 1));
    }

    @Override
    default IdGeneratorStat stat() {
        return new SnowflakeIdStat(
                getClass().getSimpleName(),
                getEpoch(),
                getTimestampBit(),
                getMachineBit(),
                getSequenceBit(),
                isSafeJavascript(),
                getMachineId(),
                getLastTimestamp(),
                idConverter().stat()
        );
    }
}
