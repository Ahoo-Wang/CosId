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

package me.ahoo.cosid.machine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

/**
 * @author : Rocher Kong
 */
class ClockSyncSnowflakeIdTest {
    public static final long TEST_MACHINE_ID = 1;
    ClockSyncSnowflakeId clockSyncSnowflakeId;

    @BeforeEach
    void setup() {
        MillisecondSnowflakeId idGen = new MillisecondSnowflakeId(TEST_MACHINE_ID);
        clockSyncSnowflakeId = new ClockSyncSnowflakeId(idGen);
    }

    @Test
    void getEpoch() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getEpoch());
    }

    @Test
    void getTimestampBit() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getTimestampBit());
    }

    @Test
    void getMachineBit() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMachineBit());
    }

    @Test
    void getSequenceBit() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getSequenceBit());
    }

    @Test
    void isSafeJavascript() {
        Assertions.assertNotNull(clockSyncSnowflakeId.isSafeJavascript());
    }

    @Test
    void getMaxTimestamp() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMaxTimestamp());
    }

    @Test
    void getMaxMachine() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMaxMachine());
    }

    @Test
    void getMaxSequence() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMaxSequence());
    }

    @Test
    void getLastTimestamp() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getLastTimestamp());
    }

    @Test
    void getMachineId() {
        Assertions.assertNotNull(clockSyncSnowflakeId.getMachineId());
    }
}
