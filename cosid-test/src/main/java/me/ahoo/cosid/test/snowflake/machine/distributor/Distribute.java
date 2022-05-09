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

package me.ahoo.cosid.test.snowflake.machine.distributor;

import static me.ahoo.cosid.test.snowflake.machine.distributor.MachineIdDistributorSpec.TEST_MACHINE_BIT;
import static me.ahoo.cosid.test.snowflake.machine.distributor.MachineIdDistributorSpec.mockInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdDistributor;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.TestSpec;

import java.util.function.Supplier;

/**
 * Distribute .
 *
 * @author ahoo wang
 */
public class Distribute implements TestSpec {
    private final Supplier<MachineIdDistributor> implFactory;
    
    public Distribute(Supplier<MachineIdDistributor> implFactory) {
        this.implFactory = implFactory;
    }
    
    @Override
    public void verify() {
        MachineIdDistributor distributor = implFactory.get();
        String namespace = MockIdGenerator.usePrefix("Distribute").generateAsString();
        InstanceId instanceId = mockInstance(0, false);
        int machineId = distributor.distribute(namespace, TEST_MACHINE_BIT, instanceId);
        assertThat(machineId, equalTo(0));
    }
}
