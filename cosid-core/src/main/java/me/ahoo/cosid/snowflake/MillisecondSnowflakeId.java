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
 * Millisecond-precision Snowflake ID generator.
 * 
 * <p>This implementation of the Snowflake algorithm uses millisecond-precision
 * timestamps as the time component of generated IDs. It provides a good balance
 * between time resolution and longevity:
 * 
 * <ul>
 *   <li>41 bits for timestamp (millisecond precision)</li>
 *   <li>10 bits for machine ID (1024 unique machines)</li>
 *   <li>12 bits for sequence (4096 IDs per millisecond per machine)</li>
 * </ul>
 * 
 * <p>With millisecond precision and 41 bits, this generator can produce unique
 * IDs for approximately 69 years from the epoch. The default epoch is set to
 * December 24, 2019, which extends the usable time range into the 2080s.
 * 
 * <p>This class extends {@link AbstractSnowflakeId} and provides the concrete
 * implementation for retrieving the current time in milliseconds.
 *
 * @author ahoo wang
 */
public class MillisecondSnowflakeId extends AbstractSnowflakeId {
    
    /**
     * The default number of bits used for the timestamp portion (41 bits).
     * 
     * <p>With 41 bits, timestamps can represent approximately 69 years
     * of millisecond-precision time, providing a good balance between
     * longevity and resolution.
     */
    public static final int DEFAULT_TIMESTAMP_BIT = 41;
    
    /**
     * The default number of bits used for the machine ID portion (10 bits).
     * 
     * <p>With 10 bits, up to 1024 unique machines can participate in ID
     * generation, which is sufficient for most distributed systems.
     */
    public static final int DEFAULT_MACHINE_BIT = 10;
    
    /**
     * The default number of bits used for the sequence portion (12 bits).
     * 
     * <p>With 12 bits, up to 4096 unique IDs can be generated per time unit
     * (per millisecond) per machine, providing high throughput within a
     * single time unit.
     */
    public static final int DEFAULT_SEQUENCE_BIT = 12;
    
    /**
     * The default sequence reset threshold (half of the maximum sequence value).
     * 
     * <p>This threshold is used to determine when to reset the sequence counter
     * to avoid reaching the maximum sequence value too quickly.
     */
    public static final long DEFAULT_SEQUENCE_RESET_THRESHOLD = ~(-1L << (DEFAULT_SEQUENCE_BIT - 1));
    
    /**
     * Create a new MillisecondSnowflakeId with default configuration.
     * 
     * <p>This constructor creates a generator with the default bit configuration
     * and the CosId epoch, intended for use with the specified machine ID.
     *
     * @param machineId The machine ID for this generator (0-1023)
     */
    public MillisecondSnowflakeId(int machineId) {
        this(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }
    
    /**
     * Create a new MillisecondSnowflakeId with custom machine bit configuration.
     * 
     * <p>This constructor allows customization of the machine bit size while
     * using default values for other parameters.
     *
     * @param machineBit The number of bits to use for machine ID
     * @param machineId The machine ID for this generator
     */
    public MillisecondSnowflakeId(int machineBit, int machineId) {
        super(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, machineBit, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }
    
    /**
     * Create a new MillisecondSnowflakeId with custom bit configuration.
     * 
     * <p>This constructor allows customization of the bit sizes while using
     * the default sequence reset threshold calculation.
     *
     * @param epoch The epoch timestamp to use as the base
     * @param timestampBit The number of bits to use for timestamp
     * @param machineBit The number of bits to use for machine ID
     * @param sequenceBit The number of bits to use for sequence
     * @param machineId The machine ID for this generator
     */
    public MillisecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId, SnowflakeId.defaultSequenceResetThreshold(sequenceBit));
    }
    
    /**
     * Create a new MillisecondSnowflakeId with full custom configuration.
     * 
     * <p>This constructor allows complete control over all parameters of the
     * Snowflake ID generator.
     *
     * @param epoch The epoch timestamp to use as the base
     * @param timestampBit The number of bits to use for timestamp
     * @param machineBit The number of bits to use for machine ID
     * @param sequenceBit The number of bits to use for sequence
     * @param machineId The machine ID for this generator
     * @param sequenceResetThreshold The threshold for sequence reset
     */
    public MillisecondSnowflakeId(long epoch, int timestampBit, int machineBit, int sequenceBit, int machineId, long sequenceResetThreshold) {
        super(epoch, timestampBit, machineBit, sequenceBit, machineId, sequenceResetThreshold);
    }
    
    /**
     * Get the current time in milliseconds.
     * 
     * <p>This method provides the time source for the timestamp portion of
     * generated IDs. It uses {@link System#currentTimeMillis()} to retrieve
     * the current time with millisecond precision.
     *
     * @return The current time in milliseconds since the Unix epoch
     */
    @Override
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }
}
