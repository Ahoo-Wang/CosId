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

import java.util.concurrent.TimeUnit;

/**
 * Second-based Snowflake ID generator.
 *
 * <p>Similar to {@link MillisecondSnowflakeId} but uses seconds instead of milliseconds
 * as the time unit. This allows for a longer timestamp range but with lower
 * time precision.
 *
 * <p>Default configuration:
 * <ul>
 *   <li>Timestamp bits: 31 (about 68 years with second precision)</li>
 *   <li>Machine ID bits: 10 (1024 unique machines)</li>
 *   <li>Sequence bits: 22 (about 4 million IDs per second per machine)</li>
 * </ul>
 *
 * @author ahoo wang
 */
public class SecondSnowflakeId extends AbstractSnowflakeId {

    /**
     * Default number of timestamp bits (31 bits).
     */
    public static final int DEFAULT_TIMESTAMP_BIT = 31;
    /**
     * Default number of machine ID bits (10 bits).
     */
    public static final int DEFAULT_MACHINE_BIT = 10;
    /**
     * Default number of sequence bits (22 bits).
     */
    public static final int DEFAULT_SEQUENCE_BIT = 22;
    /**
     * Default sequence reset threshold (half of max sequence).
     */
    public static final long DEFAULT_SEQUENCE_RESET_THRESHOLD = ~(-1L << (DEFAULT_SEQUENCE_BIT - 1));

    /**
     * Creates a SecondSnowflakeId with default configuration.
     *
     * @param machineId the machine ID
     */
    public SecondSnowflakeId(int machineId) {
        this(CosId.COSID_EPOCH_SECOND, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }

    /**
     * Creates a SecondSnowflakeId with custom machine bits.
     *
     * @param machineBit the number of bits for machine ID
     * @param machineId  the machine ID
     */
    public SecondSnowflakeId(int machineBit, int machineId) {
        super(CosId.COSID_EPOCH_SECOND, DEFAULT_TIMESTAMP_BIT, machineBit, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }

    /**
     * Creates a SecondSnowflakeId with custom bit configuration.
     *
     * @param epoch        epoch timestamp in milliseconds
     * @param timestampBit number of bits for timestamp
     * @param machineBit   number of bits for machine ID
     * @param sequenceBit  number of bits for sequence
     * @param machineId    the machine ID
     */
    public SecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId, SnowflakeId.defaultSequenceResetThreshold(sequenceBit));
    }

    /**
     * Creates a SecondSnowflakeId with full custom configuration.
     *
     * @param epoch                   epoch timestamp in milliseconds
     * @param timestampBit            number of bits for timestamp
     * @param machineBit              number of bits for machine ID
     * @param sequenceBit             number of bits for sequence
     * @param machineId               the machine ID
     * @param sequenceResetThreshold  threshold for resetting sequence
     */
    public SecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId, sequenceResetThreshold);
    }

    @Override
    protected long getCurrentTime() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
}
