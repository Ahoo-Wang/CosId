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

import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

/**
 * @author ahoo wang
 */
class StatefulSetMachineIdDistributorTest {
    
    @Test
    @SetEnvironmentVariable(
        key = StatefulSetMachineIdDistributor.HOSTNAME_KEY,
        value = "cosid-host-6")
    void resolveMachineId() {
        int machineId = StatefulSetMachineIdDistributor.resolveMachineId();
        assertThat(machineId, equalTo(6));
    }
    
    @Test
    @SetEnvironmentVariable(
        key = StatefulSetMachineIdDistributor.HOSTNAME_KEY,
        value = "cosid-host-6")
    void distribute() {
        int machineId = StatefulSetMachineIdDistributor.INSTANCE
            .distribute("k8s", 1, InstanceId.NONE, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION).getMachineId();
        Assertions.assertEquals(6, machineId);
    }
    
    @Test
    void revert() {
        StatefulSetMachineIdDistributor.INSTANCE.revert("k8s", InstanceId.NONE);
    }
}
