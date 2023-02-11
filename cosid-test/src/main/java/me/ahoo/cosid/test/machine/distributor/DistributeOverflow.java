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

package me.ahoo.cosid.test.machine.distributor;

import static me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec.allInstancesMoreOne;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.TestSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * DistributeOverflow .
 *
 * @author ahoo wang
 */
public class DistributeOverflow implements TestSpec {
    private final Supplier<MachineIdDistributor> implFactory;
    private final int machineBit;
    
    public DistributeOverflow(Supplier<MachineIdDistributor> implFactory, int machineBit) {
        this.implFactory = implFactory;
        this.machineBit = machineBit;
    }
    
    @Override
    public void verify() {
        MachineIdDistributor distributor = implFactory.get();
        String namespace = MockIdGenerator.usePrefix("DistributeOverflow").generateAsString();
        List<InstanceId> allInstances = allInstancesMoreOne(machineBit, false);
        assertThat(allInstances, hasSize(MachineIdDistributor.totalMachineIds(machineBit) + 1));
        
        for (int i = 0; i < allInstances.size() - 1; i++) {
            int machineId = distributor.distribute(namespace, machineBit, allInstances.get(i), MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION).getMachineId();
            assertThat(machineId, equalTo(i));
        }
        
        InstanceId overflowInstanceId = allInstances.get(MachineIdDistributor.totalMachineIds(machineBit));
        Assert.assertThrows(MachineIdOverflowException.class, () -> {
            distributor.distribute(namespace, machineBit, overflowInstanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        });
        
    }
}
