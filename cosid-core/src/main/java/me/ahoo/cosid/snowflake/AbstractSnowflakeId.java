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

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.internal.TimestampSequencer;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.TimestampOverflowException;


/**
 * Abstract SnowflakeId implementation.
 *
 * <p>This abstract class provides the base implementation for Snowflake ID generation,
 * handling the common logic for timestamp management, sequence counting, and ID assembly.
 * Subclasses implement {@link #getCurrentTime()} to provide time in different units
 * (milliseconds, seconds, etc.).
 *
 * <p>The ID is composed of: timestamp (configurable bits) + machine ID (configurable bits) + sequence (configurable bits)
 *
 * @author ahoo wang
 */
public abstract class AbstractSnowflakeId implements SnowflakeId {

    /**
     * Epoch timestamp used as the base for time calculations.
     */
    protected final long epoch;
    /**
     * Number of bits allocated for the timestamp portion.
     */
    protected final int timestampBit;
    /**
     * Number of bits allocated for the machine ID portion.
     */
    protected final int machineBit;
    /**
     * Number of bits allocated for the sequence portion.
     */
    protected final int sequenceBit;

    /**
     * Maximum timestamp value representable by the timestamp bits.
     */
    protected final long maxTimestamp;
    /**
     * Maximum sequence value representable by the sequence bits.
     */
    protected final long maxSequence;
    /**
     * Maximum machine ID value representable by the machine ID bits.
     */
    protected final int maxMachineId;

    /**
     * Number of bits to shift machine ID left (equal to sequenceBit).
     */
    protected final long machineLeft;
    /**
     * Number of bits to shift timestamp left (equal to sequenceBit + machineBit).
     */
    protected final long timestampLeft;
    /**
     * The machine ID value for this instance.
     *
     * <p>Note: When machineLeft is greater than 30, overflow can occur during calculation,
     * so machineId should be kept as long during arithmetic operations.
     */
    protected final long machineId;
    /**
     * Timestamp and sequence logic, shared with the CosId generator.
     */
    private final TimestampSequencer sequencer;
    /**
     * Sequence of the last generated ID.
     *
     * <p>A read-only mirror of the {@link TimestampSequencer} state: changing it has no effect.</p>
     */
    protected long sequence = 0L;
    /**
     * Timestamp of the last generated ID.
     *
     * <p>A read-only mirror of the {@link TimestampSequencer} state: changing it has no effect.</p>
     */
    protected long lastTimestamp = -1L;
    /**
     * The bit layout.
     */
    private final SnowflakeLayout layout;

    /**
     * Creates a new AbstractSnowflakeId.
     *
     * @param epoch                   epoch timestamp in milliseconds
     * @param timestampBit           number of bits for timestamp
     * @param machineBit              number of bits for machine ID
     * @param sequenceBit            number of bits for sequence
     * @param machineId              the machine ID value
     * @param sequenceResetThreshold threshold for resetting sequence on timestamp advance
     * @throws IllegalArgumentException if total bits exceed 63 or machineId is invalid
     */
    public AbstractSnowflakeId(long epoch,
                               int timestampBit,
                               int machineBit,
                               int sequenceBit,
                               int machineId,
                               long sequenceResetThreshold) {
        this.layout = new SnowflakeLayout(epoch, timestampBit, machineBit, sequenceBit);
        this.epoch = epoch;
        this.timestampBit = timestampBit;
        this.machineBit = machineBit;
        this.sequenceBit = sequenceBit;
        this.maxTimestamp = ~(-1L << timestampBit);
        this.maxSequence = ~(-1L << sequenceBit);
        this.maxMachineId = ~(-1 << machineBit);
        this.machineLeft = sequenceBit;
        this.timestampLeft = this.machineLeft + machineBit;
        if (machineId > this.maxMachineId || machineId < 0) {
            throw new IllegalArgumentException(Checks.lenientFormat("machineId[%s] can't be greater than maxMachineId[%s] or less than 0 .", machineId, maxMachineId));
        }
        this.machineId = machineId;
        this.sequencer = new TimestampSequencer(this::getCurrentTime, maxSequence, sequenceResetThreshold);
    }

    protected abstract long getCurrentTime();

    /**
     * Generates the next unique ID.
     *
     * <p>This method is synchronized to ensure thread-safe ID generation.
     *
     * @return a unique snowflake ID
     * @throws ClockBackwardsException   if system clock has moved backwards
     * @throws TimestampOverflowException if timestamp exceeds maximum value
     */
    @Override
    public synchronized long generate() {
        long currentTimestamp = sequencer.next();
        sequence = sequencer.getSequence();
        lastTimestamp = currentTimestamp;
        long diffTimestamp = (currentTimestamp - epoch);
        if (diffTimestamp > maxTimestamp) {
            throw new TimestampOverflowException(epoch, diffTimestamp, maxTimestamp);
        }
        return diffTimestamp << timestampLeft
                | machineId << machineLeft
                | sequence;
    }

    @Override
    public SnowflakeLayout layout() {
        return layout;
    }

    @Override
    public long getEpoch() {
        return epoch;
    }

    @Override
    public int getTimestampBit() {
        return timestampBit;
    }

    @Override
    public int getMachineBit() {
        return machineBit;
    }

    @Override
    public int getSequenceBit() {
        return sequenceBit;
    }

    @Override
    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    @Override
    public int getMaxMachineId() {
        return maxMachineId;
    }

    @Override
    public long getMaxSequence() {
        return maxSequence;
    }

    @Override
    public long getLastTimestamp() {
        return lastTimestamp;
    }

    @Override
    public int getMachineId() {
        return (int) machineId;
    }

}
