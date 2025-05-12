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

package me.ahoo.cosid.proxy;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.NotFoundMachineStateException;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;

import org.junit.jupiter.api.Test;

class ProxyMachineIdDistributorTest extends MachineIdDistributorSpec {
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new ProxyMachineIdDistributor(ProxyServerLauncher.createMachineApi(), MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    public void guardLost() {
        //ignore
    }
    
    @Test
    public void guardIfNotFoundMachineState() {
        MachineIdDistributor distributor = getDistributor();
        String namespace = MockIdGenerator.usePrefix("guardIfNotFoundMachineState").generateAsString();
        InstanceId instanceId = mockInstance(0, false);
        MachineStateStorage.IN_MEMORY.set(namespace, 10, instanceId);
        
        Assert.assertThrows(NotFoundMachineStateException.class, () -> {
            distributor.guard(namespace, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        });
        
    }
}
