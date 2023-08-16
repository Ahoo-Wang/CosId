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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class ClockSyncCosIdGeneratorTest {
    private final Radix62CosIdGenerator radix62CosIdGenerator = new Radix62CosIdGenerator(1);
    private final ClockSyncCosIdGenerator clockSyncCosIdGenerator = new ClockSyncCosIdGenerator(radix62CosIdGenerator);
    
    @Test
    void getActual() {
        assertThat(clockSyncCosIdGenerator.getActual(), sameInstance(radix62CosIdGenerator));
    }
    
    @Test
    void getMachineId() {
        assertThat(clockSyncCosIdGenerator.getMachineId(), equalTo(1));
    }
    
    @Test
    void getLastTimestamp() {
        assertThat(clockSyncCosIdGenerator.getLastTimestamp(), equalTo(radix62CosIdGenerator.getLastTimestamp()));
    }
    
    @Test
    void getStateParser() {
        assertThat(clockSyncCosIdGenerator.getStateParser(), equalTo(radix62CosIdGenerator.getStateParser()));
    }
    
    @Test
    void generateAsState() {
        CosIdState state1 = clockSyncCosIdGenerator.generateAsState();
        CosIdState state2 = clockSyncCosIdGenerator.generateAsState();
        assertThat(state2, greaterThan(state1));
    }
}
