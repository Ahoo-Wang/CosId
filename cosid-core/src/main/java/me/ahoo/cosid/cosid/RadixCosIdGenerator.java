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

import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.TimestampOverflowException;

import com.google.common.base.Strings;
import org.jspecify.annotations.NonNull;

/**
 * Radix-based CosIdGenerator implementation.
 *
 * <p>A simpler variant of Snowflake ID that uses configurable bit allocation
 * for timestamp, machine ID, and sequence components.
 *
 * @author ahoo wang
 * @see CosIdGenerator
 * @see CosIdIdStateParser
 * @see CosIdState
 * @see ClockBackwardsException
 * @see TimestampOverflowException
 */
public class RadixCosIdGenerator implements CosIdGenerator {
    /**
     * Default timestamp bits (44 bits, ~556 years at millisecond precision).
     */
    public static final int DEFAULT_TIMESTAMP_BIT = 44;
    /**
     * Default machine ID bits (20 bits).
     */
    public static final int DEFAULT_MACHINE_BIT = 20;
    /**
     * Default sequence bits (16 bits, 65536 per millisecond).
     */
    public static final int DEFAULT_SEQUENCE_BIT = 16;
    /**
     * Default sequence reset threshold (half of max sequence).
     */
    public static final int DEFAULT_SEQUENCE_RESET_THRESHOLD = ~(-1 << (DEFAULT_SEQUENCE_BIT - 1));

    private final long maxTimestamp;
    private final int maxMachine;
    private final int maxSequence;
    private final int sequenceResetThreshold;

    private final int machineId;
    private int sequence = 0;
    private long lastTimestamp = -1L;

    private final CosIdIdStateParser stateParser;

    /**
     * Creates a new RadixCosIdGenerator.
     *
     * @param timestampBit           number of bits for timestamp
     * @param machineIdBit          number of bits for machine ID
     * @param sequenceBit           number of bits for sequence
     * @param machineId             the machine ID
     * @param sequenceResetThreshold threshold for resetting sequence
     * @param stateParser           the state parser for string conversion
     */
    public RadixCosIdGenerator(int timestampBit,
                               int machineIdBit,
                               int sequenceBit,
                               int machineId,
                               int sequenceResetThreshold,
                               CosIdIdStateParser stateParser) {
        this.maxTimestamp = ~(-1L << timestampBit);
        this.maxMachine = ~(-1 << machineIdBit);
        this.maxSequence = ~(-1 << sequenceBit);
        this.sequenceResetThreshold = sequenceResetThreshold;
        if (machineId > this.maxMachine || machineId < 0) {
            throw new IllegalArgumentException(Strings.lenientFormat("machineId can't be greater than maxMachine[%s] or less than 0 .", maxMachine));
        }
        this.machineId = machineId;
        this.stateParser = stateParser;
    }

    @Override
    public int getMachineId() {
        return machineId;
    }

    @Override
    public long getLastTimestamp() {
        return lastTimestamp;
    }

    @NonNull
    @Override
    public CosIdIdStateParser getStateParser() {
        return stateParser;
    }

    private long nextTime() {
        long time = System.currentTimeMillis();
        while (time <= lastTimestamp) {
            time = System.currentTimeMillis();
        }
        return time;
    }

    /**
     * Generates the next ID as a state object.
     *
     * @return the generated CosIdState
     */
    @NonNull
    public synchronized CosIdState generateAsState() {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp < lastTimestamp) {
            throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
        }

        //region Reset sequence based on sequence reset threshold,Optimize the problem of uneven sharding.

        if (currentTimestamp > lastTimestamp
            && sequence >= sequenceResetThreshold) {
            sequence = 0;
        }

        sequence = (sequence + 1) & maxSequence;

        if (sequence == 0) {
            currentTimestamp = nextTime();
        }

        //endregion

        if (currentTimestamp > maxTimestamp) {
            throw new TimestampOverflowException(0, currentTimestamp, maxTimestamp);
        }
        lastTimestamp = currentTimestamp;
        return new CosIdState(lastTimestamp, machineId, sequence);
    }

}
