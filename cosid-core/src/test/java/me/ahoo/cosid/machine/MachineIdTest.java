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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MachineIdTest {

    @Test
    void valueObjectShouldCompareByMachineIdOnly() {
        MachineId machineId = new MachineId(7);

        assertEquals(7, machineId.getMachineId());
        assertEquals(machineId, new MachineId(7));
        assertEquals(machineId.hashCode(), new MachineId(7).hashCode());
        assertNotEquals(machineId, new MachineId(8));
    }

    @Test
    void maxMachineIdShouldReturnInclusiveUpperBoundForValidBits() {
        assertEquals(1, MachineIdDistributor.maxMachineId(1));
        assertEquals(3, MachineIdDistributor.maxMachineId(2));
        assertEquals(Integer.MAX_VALUE, MachineIdDistributor.maxMachineId(31));
        assertEquals(4, MachineIdDistributor.totalMachineIds(2));
    }

    @Test
    void maxMachineIdShouldRejectUnsupportedBitWidthsWithMessage() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
            () -> MachineIdDistributor.maxMachineId(0));
        IllegalArgumentException thirtyTwo = assertThrows(IllegalArgumentException.class,
            () -> MachineIdDistributor.maxMachineId(32));

        assertEquals("machineBit:[0] must be between 1 and 31!", zero.getMessage());
        assertEquals("machineBit:[32] must be between 1 and 31!", thirtyTwo.getMessage());
    }

    @Test
    void namespacedMachineIdShouldPadMachineIdToEightDigits() {
        assertEquals("order.00000007", MachineIdDistributor.namespacedMachineId("order", 7));
        assertEquals("order.123456789", MachineIdDistributor.namespacedMachineId("order", 123456789));
    }
}
