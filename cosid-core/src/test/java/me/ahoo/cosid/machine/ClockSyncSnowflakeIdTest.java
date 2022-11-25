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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author : Rocher Kong
 */
class ClockSyncSnowflakeIdTest {
    public static final int TEST_MACHINE_ID = 1;
    ClockSyncSnowflakeId clockSyncSnowflakeId;
    
    @BeforeEach
    void setup() {
        MillisecondSnowflakeId idGen = new MillisecondSnowflakeId(TEST_MACHINE_ID);
        clockSyncSnowflakeId = new ClockSyncSnowflakeId(idGen);
    }
    
    @Test
    void getEpoch() {
        assertThat(clockSyncSnowflakeId.getEpoch(), equalTo(CosId.COSID_EPOCH));
    }
    
    @Test
    void getTimestampBit() {
        assertThat(clockSyncSnowflakeId.getTimestampBit(), equalTo(41));
    }
    
    @Test
    void getMachineBit() {
        assertThat(clockSyncSnowflakeId.getMachineBit(), equalTo(10));
    }
    
    @Test
    void getSequenceBit() {
        assertThat(clockSyncSnowflakeId.getSequenceBit(), equalTo(12));
    }
    
    @Test
    void isSafeJavascript() {
        assertThat(clockSyncSnowflakeId.isSafeJavascript(), equalTo(false));
    }
    
    @Test
    void getMaxTimestamp() {
        assertThat(clockSyncSnowflakeId.getMaxTimestamp(), greaterThan(0L));
    }
    
    @Test
    void getMaxMachine() {
        assertThat(clockSyncSnowflakeId.getMaxMachine(), equalTo(1023));
    }
    
    @Test
    void getMaxSequence() {
        assertThat(clockSyncSnowflakeId.getMaxSequence(), equalTo(4095L));
    }
    
    @Test
    void getLastTimestamp() {
        clockSyncSnowflakeId.generate();
        assertThat(clockSyncSnowflakeId.getLastTimestamp(), greaterThan(0L));
    }
    
    @Test
    void getMachineId() {
        assertThat(clockSyncSnowflakeId.getMachineId(), equalTo(TEST_MACHINE_ID));
    }
}
