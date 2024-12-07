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

import static me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec.mockInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.TestSpec;

import lombok.SneakyThrows;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Guard .
 *
 * @author ahoo wang
 */
public class Guard implements TestSpec {
    private final Supplier<MachineIdDistributor> implFactory;
    private final int machineBit;

    public Guard(Supplier<MachineIdDistributor> implFactory, int machineBit) {
        this.implFactory = implFactory;
        this.machineBit = machineBit;
    }

    @SneakyThrows
    @Override
    public void verify() {
        MachineIdDistributor distributor = implFactory.get();
        String namespace = MockIdGenerator.usePrefix("Guard").generateAsString();
        int port = ThreadLocalRandom.current().nextInt(0, 65535);
        InstanceId instanceId = mockInstance(port, false);
        int machineId = distributor.distribute(namespace, machineBit, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION).getMachineId();
        assertThat(machineId, equalTo(0));
        Thread.sleep(50);
        distributor.guard(namespace, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
    }
}
