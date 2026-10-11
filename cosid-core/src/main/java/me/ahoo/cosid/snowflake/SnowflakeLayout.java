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

/**
 * The bit layout of a snowflake id: {@code timestamp | machine id | sequence}, from the high bits to the low bits, with
 * the timestamp counted from {@code epoch}.
 *
 * @param epoch        the time the timestamp counts from, in the generator's time unit (milliseconds or seconds)
 * @param timestampBit bits of the timestamp
 * @param machineBit   bits of the machine id
 * @param sequenceBit  bits of the sequence
 * @author ahoo wang
 * @since 4.0.0
 */
public record SnowflakeLayout(long epoch, int timestampBit, int machineBit, int sequenceBit) {
    
    /**
     * Validates the layout.
     *
     * @throws IllegalArgumentException if a bit count is negative, {@code machineBit} is 32 or more, or the total exceeds
     *     {@link SnowflakeId#TOTAL_BIT}
     */
    public SnowflakeLayout {
        if (timestampBit < 0 || machineBit < 0 || sequenceBit < 0) {
            throw new IllegalArgumentException("bit counts can't be negative: timestampBit[" + timestampBit + "], machineBit[" + machineBit
                + "], sequenceBit[" + sequenceBit + "].");
        }
        if (machineBit >= Integer.SIZE) {
            throw new IllegalArgumentException("machineBit[" + machineBit + "] must be less than 32: machine ids are ints.");
        }
        if (timestampBit + machineBit + sequenceBit > SnowflakeId.TOTAL_BIT) {
            throw new IllegalArgumentException("total bit can't be greater than TOTAL_BIT[63] .");
        }
    }
    
    public long maxTimestamp() {
        return ~(-1L << timestampBit);
    }
    
    public int maxMachineId() {
        return ~(-1 << machineBit);
    }
    
    public long maxSequence() {
        return ~(-1L << sequenceBit);
    }
    
    /**
     * Left shift of the machine id: the sequence bits.
     */
    public int machineLeft() {
        return sequenceBit;
    }
    
    /**
     * Left shift of the timestamp: the machine id and sequence bits.
     */
    public int timestampLeft() {
        return sequenceBit + machineBit;
    }
    
    /**
     * Whether every id fits in a JavaScript number without losing precision.
     */
    public boolean isSafeJavascript() {
        return timestampBit + machineBit + sequenceBit <= SafeJavaScriptSnowflakeId.JAVA_SCRIPT_MAX_SAFE_NUMBER_BIT;
    }
}
