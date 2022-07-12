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

import static me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec.TEST_MACHINE_BIT;
import static me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec.mockInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.TestSpec;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * DistributeConcurrent .
 *
 * @author ahoo wang
 */
public class DistributeConcurrent implements TestSpec {
    private final Supplier<MachineIdDistributor> implFactory;
    
    public DistributeConcurrent(Supplier<MachineIdDistributor> implFactory) {
        this.implFactory = implFactory;
    }
    
    @Override
    public void verify() {
        int machineBit = TEST_MACHINE_BIT;
        MachineIdDistributor distributor = implFactory.get();
        int totalMachineIds = MachineIdDistributor.totalMachineIds(machineBit);
        CompletableFuture<Integer>[] results = new CompletableFuture[totalMachineIds];
        String namespace = MockIdGenerator.usePrefix("DistributeConcurrent").generateAsString();
        
        for (int i = 0; i < totalMachineIds; i++) {
            InstanceId instanceId = mockInstance(i, false);
            results[i] = CompletableFuture.supplyAsync(() -> distributor.distribute(namespace, machineBit, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION).getMachineId());
        }
        
        CompletableFuture.allOf(results).join();
        
        Integer[] machineIds = Arrays.stream(results).map(CompletableFuture::join).sorted().toArray(Integer[]::new);
        for (int i = 0; i < machineIds.length; i++) {
            assertThat(machineIds[i], equalTo(i));
        }
    }
}
