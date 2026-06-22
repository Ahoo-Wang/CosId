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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.stat.generator.SnowflakeIdStat;

import org.junit.jupiter.api.Test;

class SnowflakeIdTest {

    @Test
    void getLastTimestampAsMillisecondsShouldDefaultToLastTimestamp() {
        StubSnowflakeId snowflakeId = new StubSnowflakeId(41, 10, 12, 123);

        assertEquals(123, snowflakeId.getLastTimestampAsMilliseconds());
    }

    @Test
    void defaultSequenceResetThresholdShouldUseHalfOfSequenceCapacity() {
        assertEquals(0, SnowflakeId.defaultSequenceResetThreshold(1));
        assertEquals(7, SnowflakeId.defaultSequenceResetThreshold(4));
        assertEquals(2047, SnowflakeId.defaultSequenceResetThreshold(12));
    }

    @Test
    void isSafeJavascriptShouldDependOnConfiguredTotalBits() {
        assertTrue(new StubSnowflakeId(41, 10, 2, 123).isSafeJavascript());
        assertFalse(new StubSnowflakeId(41, 10, 12, 123).isSafeJavascript());
    }

    @Test
    void statShouldExposeConfigurationAndCurrentState() {
        StubSnowflakeId snowflakeId = new StubSnowflakeId(41, 10, 12, 123);

        SnowflakeIdStat stat = (SnowflakeIdStat) snowflakeId.stat();

        assertEquals("StubSnowflakeId", stat.getKind());
        assertEquals(100, stat.getEpoch());
        assertEquals(41, stat.getTimestampBit());
        assertEquals(10, stat.getMachineBit());
        assertEquals(12, stat.getSequenceBit());
        assertFalse(stat.isSafeJavascript());
        assertEquals(5, stat.getMachineId());
        assertEquals(123, stat.getLastTimestamp());
        assertEquals(ToStringIdConverter.INSTANCE.stat(), stat.getConverter());
    }

    private record StubSnowflakeId(int timestampBit, int machineBit, int sequenceBit, long lastTimestamp) implements SnowflakeId {
        @Override
        public long getEpoch() {
            return 100;
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
            return ~(-1L << timestampBit);
        }

        @Override
        public int getMaxMachineId() {
            return ~(-1 << machineBit);
        }

        @Override
        public long getMaxSequence() {
            return ~(-1L << sequenceBit);
        }

        @Override
        public long getLastTimestamp() {
            return lastTimestamp;
        }

        @Override
        public int getMachineId() {
            return 5;
        }

        @Override
        public ToStringIdConverter idConverter() {
            return ToStringIdConverter.INSTANCE;
        }

        @Override
        public long generate() {
            return 1;
        }
    }
}
