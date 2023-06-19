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
import org.junit.jupiter.api.Test;

/**
 * MachineIdTest .
 *
 * @author ahoo wang
 */
class MachineIdTest {
    
    @Test
    void getMachineId() {
        MachineId machineId = new MachineId(1);
        Assertions.assertEquals(1, machineId.getMachineId());
    }
    
    @Test
    void testEquals() {
        MachineId machineId1 = new MachineId(1);
        MachineId machineId2 = new MachineId(1);
        Assertions.assertEquals(machineId1, machineId2);
    }
    
    @Test
    void testHashCode() {
        MachineId machineId1 = new MachineId(1);
        MachineId machineId2 = new MachineId(1);
        Assertions.assertEquals(machineId1.hashCode(), machineId2.hashCode());
    }
}
