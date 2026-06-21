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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SnowflakeIdTest {

    @Test
    void getLastTimestampAsMillisecondsShouldDefaultToLastTimestamp() {
        SnowflakeId snowflakeId = new StubSnowflakeId(123);

        Assertions.assertEquals(123, snowflakeId.getLastTimestampAsMilliseconds());
    }

    private record StubSnowflakeId(long lastTimestamp) implements SnowflakeId {
        @Override
        public long getEpoch() {
            return 0;
        }

        @Override
        public int getTimestampBit() {
            return 1;
        }

        @Override
        public int getMachineBit() {
            return 1;
        }

        @Override
        public int getSequenceBit() {
            return 1;
        }

        @Override
        public long getMaxTimestamp() {
            return 1;
        }

        @Override
        public int getMaxMachineId() {
            return 1;
        }

        @Override
        public long getMaxSequence() {
            return 1;
        }

        @Override
        public long getLastTimestamp() {
            return lastTimestamp;
        }

        @Override
        public int getMachineId() {
            return 1;
        }

        @Override
        public long generate() {
            return 1;
        }
    }
}
