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
 * Safe JavaScript Number Id
 * Number.MAX_SAFE_INTEGER = 9007199254740991
 * Math.log2(Number.MAX_SAFE_INTEGER) = 53
 *
 * @author ahoo wang
 * Creation time 2020/9/22 21:25
 **/
public final class SafeJavaScriptSnowflakeId {

    public static final int JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT = 53;
    public static final long JAVA_SCRIPT_MAX_SAFE_NUMBER = 9007199254740991L;

    public static boolean isSafeJavaScript(long id) {
        return id < JAVA_SCRIPT_MAX_SAFE_NUMBER;
    }

    public static MillisecondSnowflakeId ofMillisecond(long epoch, int timestampBit, int machineBit, int sequenceBit, long machineId) {
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return new MillisecondSnowflakeId(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    public static SecondSnowflakeId ofSecond(long epoch, int timestampBit, int machineBit, int sequenceBit, long machineId) {
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return new SecondSnowflakeId(epoch, timestampBit, machineBit, sequenceBit, machineId);
    }

    /**
     * Max Sequence (9 bits) = ((1&lt;&lt;)*1000) = 512000 (TPS)
     * Max Machine (3 bits) = 1&lt;&lt;3 = 8
     * Max Timestamp = 2199023255551 ms ~~ 69.7 years
     *
     * @param machineId 服务实例编号
     * @return MillisecondSnowflakeId
     */
    public static MillisecondSnowflakeId ofMillisecond(long machineId) {
        final int timestampBit = MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        final int machineBit = MillisecondSnowflakeId.DEFAULT_MACHINE_BIT - 7;
        final int sequenceBit = MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT - 3;
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return ofMillisecond(CosId.COSID_EPOCH_SECOND, timestampBit, machineBit, sequenceBit, machineId);
    }

    /**
     * Max Sequence (19 bits) = (1&lt;&lt;19) = 524288 (TPS)
     * Max Machine (3 bits) = 1&lt;&lt;3 = 8
     * Max Timestamp = 2147483647 s ~~ 68 years
     *
     * @param machineId 服务实例编号
     * @return SecondSnowflakeId
     */
    public static SecondSnowflakeId ofSecond(long machineId) {
        final int timestampBit = SecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        final int machineBit = SecondSnowflakeId.DEFAULT_MACHINE_BIT - 7;
        final int sequenceBit = SecondSnowflakeId.DEFAULT_SEQUENCE_BIT - 3;
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return ofSecond(CosId.COSID_EPOCH_SECOND, timestampBit, machineBit, sequenceBit, machineId);
    }

    private static void checkTotalBit(int timestampBit, int machineBit, int sequenceBit) {
        if (timestampBit + machineBit + sequenceBit > JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT) {
            throw new IllegalArgumentException(String.format("total bit can't be greater than JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT:[%s].", JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT));
        }
    }
}
