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

import com.google.common.base.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MachineStateTest .
 *
 * @author ahoo wang
 */
class MachineStateTest {
    
    @Test
    void getMachineId() {
        Assertions.assertEquals(Objects.hashCode(MachineState.NOT_FOUND.getMachineId()), MachineState.NOT_FOUND.hashCode());
    }
    
    @Test
    void testEquals() {
        MachineState machineState1 = new MachineState(1, 0);
        MachineState machineState2 = new MachineState(1, 0);
        Assertions.assertEquals(machineState1, machineState2);
    }
    
    @Test
    void testHashCode() {
        Assertions.assertEquals(Objects.hashCode(MachineState.NOT_FOUND.getMachineId()), MachineState.NOT_FOUND.hashCode());
    }
    
    @Test
    void of() {
        Assertions.assertEquals(MachineState.NOT_FOUND,MachineState.of("-1|-1"));
    }

}
