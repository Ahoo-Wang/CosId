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

package me.ahoo.cosid.string;

import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.TimestampOverflowException;

import com.google.common.annotations.Beta;
import com.google.common.base.Strings;

/**
 * [timestamp(44)]-[machineId-(16)]-[sequence-(16)] = 76 BITS.
 */
@Beta
public class CosIdGenerator implements StringIdGenerator {
    public static final int DEFAULT_TIMESTAMP_BIT = 44;
    public static final int DEFAULT_MACHINE_BIT = 16;
    public static final int DEFAULT_SEQUENCE_BIT = 16;
    
    private final long maxTimestamp;
    private final int maxMachine;
    private final int maxSequence;
    
    private final CosIdIdStateParser stateParser;
    private final int machineId;
    private int sequence = 0;
    private long lastTimestamp = -1L;
    
    public CosIdGenerator(int machineId) {
        this(DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId);
    }
    
    public CosIdGenerator(int timestampBits, int machineIdBits, int sequenceBits, int machineId) {
        this.maxTimestamp = ~(-1L << timestampBits);
        this.maxMachine = ~(-1 << machineIdBits);
        this.maxSequence = ~(-1 << sequenceBits);
        if (machineId > this.maxMachine || machineId < 0) {
            throw new IllegalArgumentException(Strings.lenientFormat("machineId can't be greater than maxMachine[%s] or less than 0 .", maxMachine));
        }
        this.machineId = machineId;
        this.stateParser =
            new CosIdIdStateParser(new Radix62IdConverter(true, charSize(timestampBits)),
                new Radix62IdConverter(true, charSize(machineIdBits)),
                new Radix62IdConverter(true, charSize(sequenceBits)));
    }
    
    public long getLastTimestamp() {
        return lastTimestamp;
    }
    
    public CosIdIdStateParser getStateParser() {
        return stateParser;
    }
    
    static int charSize(int bits) {
        long maxId = ~(-1L << bits);
        return Radix62IdConverter.INSTANCE.asString(maxId).length();
    }
    
    private long nextTime() {
        long time = System.currentTimeMillis();
        while (time <= lastTimestamp) {
            time = System.currentTimeMillis();
        }
        return time;
    }
    
    private void generate() {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp < lastTimestamp) {
            throw new ClockBackwardsException(lastTimestamp, currentTimestamp);
        }
        
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0) {
                currentTimestamp = nextTime();
            }
        } else {
            sequence = 0;
        }
        if (currentTimestamp > maxTimestamp) {
            throw new TimestampOverflowException(0, currentTimestamp, maxTimestamp);
        }
        lastTimestamp = currentTimestamp;
    }
    
    public synchronized CosIdState generateAsState() {
        generate();
        return new CosIdState(lastTimestamp, machineId, sequence);
    }
    
    @Override
    public String generateAsString() {
        CosIdState state = generateAsState();
        return stateParser.asString(state);
    }
}
