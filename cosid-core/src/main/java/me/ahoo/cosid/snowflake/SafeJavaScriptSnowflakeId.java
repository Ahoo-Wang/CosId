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
 * Safe JavaScript Snowflake ID generators.
 *
 * <p>JavaScript Numbers can only safely represent integers up to 2^53 - 1
 * (Number.MAX_SAFE_INTEGER = 9007199254740991). This class provides factory
 * methods for creating SnowflakeId instances that stay within this limit
 * by reducing total bits to 53 or fewer.
 *
 * @author ahoo wang
 **/
public final class SafeJavaScriptSnowflakeId {

    /**
     * Maximum safe JavaScript number bit count.
     */
    public static final int JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT = 53;
    /**
     * Maximum safe JavaScript number value.
     */
    public static final long JAVA_SCRIPT_MAX_SAFE_NUMBER = 9007199254740991L;

    /**
     * Checks if an ID is safe for JavaScript.
     *
     * @param id the ID to check
     * @return true if less than MAX_SAFE_NUMBER
     */
    public static boolean isSafeJavaScript(long id) {
        return id < JAVA_SCRIPT_MAX_SAFE_NUMBER;
    }

    /**
     * Creates a safe millisecond SnowflakeId.
     *
     * @param epoch                   epoch timestamp
     * @param timestampBit           bits for timestamp
     * @param machineBit            bits for machine ID
     * @param sequenceBit           bits for sequence
     * @param machineId              the machine ID
     * @param sequenceResetThreshold threshold for sequence reset
     * @return a new MillisecondSnowflakeId
     */
    public static MillisecondSnowflakeId ofMillisecond(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return new MillisecondSnowflakeId(epoch, timestampBit, machineBit, sequenceBit, machineId, sequenceResetThreshold);
    }

    /**
     * Creates a safe millisecond SnowflakeId with default safe configuration.
     *
     * <p>Default safe configuration:
     * <ul>
     *   <li>Timestamp: 41 bits</li>
     *   <li>Machine: 3 bits</li>
     *   <li>Sequence: 9 bits</li>
     * </ul>
     *
     * @param machineId the machine ID (max 7)
     * @return MillisecondSnowflakeId
     */
    public static MillisecondSnowflakeId ofMillisecond(int machineId) {
        final int timestampBit = MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        final int machineBit = MillisecondSnowflakeId.DEFAULT_MACHINE_BIT - 7;
        final int sequenceBit = MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT - 3;
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return ofMillisecond(CosId.COSID_EPOCH_SECOND, timestampBit, machineBit, sequenceBit, machineId, SnowflakeId.defaultSequenceResetThreshold(sequenceBit));
    }

    /**
     * Creates a safe second SnowflakeId.
     *
     * @param epoch                   epoch timestamp
     * @param timestampBit           bits for timestamp
     * @param machineBit            bits for machine ID
     * @param sequenceBit           bits for sequence
     * @param machineId              the machine ID
     * @param sequenceResetThreshold threshold for sequence reset
     * @return a new SecondSnowflakeId
     */
    public static SecondSnowflakeId ofSecond(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return new SecondSnowflakeId(epoch, timestampBit, machineBit, sequenceBit, machineId, sequenceResetThreshold);
    }

    /**
     * Creates a safe second SnowflakeId with default safe configuration.
     *
     * <p>Default safe configuration:
     * <ul>
     *   <li>Timestamp: 31 bits</li>
     *   <li>Machine: 3 bits</li>
     *   <li>Sequence: 19 bits</li>
     * </ul>
     *
     * @param machineId the machine ID (max 7)
     * @return SecondSnowflakeId
     */
    public static SecondSnowflakeId ofSecond(int machineId) {
        final int timestampBit = SecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        final int machineBit = SecondSnowflakeId.DEFAULT_MACHINE_BIT - 7;
        final int sequenceBit = SecondSnowflakeId.DEFAULT_SEQUENCE_BIT - 3;
        checkTotalBit(timestampBit, machineBit, sequenceBit);
        return ofSecond(CosId.COSID_EPOCH_SECOND, timestampBit, machineBit, sequenceBit, machineId, SnowflakeId.defaultSequenceResetThreshold(sequenceBit));
    }

    private static void checkTotalBit(int timestampBit, int machineBit, int sequenceBit) {
        if (timestampBit + machineBit + sequenceBit > JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT) {
            throw new IllegalArgumentException(String.format("total bit can't be greater than JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT:[%s].", JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT));
        }
    }
}
