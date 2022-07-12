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

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.machine.NotFoundMachineStateException;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ManualMachineIdDistributorTest .
 *
 * @author ahoo wang
 */
class ManualMachineIdDistributorTest {
    public static final int TEST_MANUAL_MACHINE_ID = 1;
    ManualMachineIdDistributor machineIdDistributor;
    
    @BeforeEach
    void setup() {
        machineIdDistributor = new ManualMachineIdDistributor(TEST_MANUAL_MACHINE_ID, MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Test
    void getMachineId() {
        Assertions.assertEquals(TEST_MANUAL_MACHINE_ID, machineIdDistributor.getMachineId());
    }
    
    @Test
    void distribute() {
        Assertions.assertEquals(TEST_MANUAL_MACHINE_ID,
            machineIdDistributor.distribute(MockIdGenerator.INSTANCE.generateAsString(), TEST_MANUAL_MACHINE_ID, InstanceId.NONE, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION).getMachineId());
    }
    
    @Test
    void revert() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        machineIdDistributor.distribute(namespace, TEST_MANUAL_MACHINE_ID, InstanceId.NONE, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        machineIdDistributor.revert(namespace, InstanceId.NONE);
    }
    
    @Test
    void revertNone() {
        Assertions.assertThrows(NotFoundMachineStateException.class, () -> {
            machineIdDistributor.revert(MockIdGenerator.INSTANCE.generateAsString(), InstanceId.NONE);
        });
    }
}
