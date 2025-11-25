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

import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * DefaultMachineIdGuarderTest .
 *
 * @author ahoo wang
 */
class DefaultMachineIdGuarderTest {
    private final ManualMachineIdDistributor distributor = new ManualMachineIdDistributor(1, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);

    @Test
    void register() {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(MockIdGenerator.INSTANCE.generateAsString(), InstanceId.NONE);
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        guarder.register(namespacedInstanceId.getNamespace(), namespacedInstanceId.getInstanceId());
        assertThat(guarder.getGuardianStates().keySet(), hasItem(namespacedInstanceId));
        assertThat(guarder.hasFailure(), equalTo(false));
        guarder.safeGuard();
    }

    @Test
    void registerIfNullNamespace() {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(MockIdGenerator.INSTANCE.generateAsString(), InstanceId.NONE);
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        Assertions.assertThrows(IllegalArgumentException.class, () -> guarder.register(null, namespacedInstanceId.getInstanceId()));
    }

    @Test
    void unregister() {
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId(MockIdGenerator.INSTANCE.generateAsString(), InstanceId.NONE);
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        guarder.register(namespacedInstanceId.getNamespace(), namespacedInstanceId.getInstanceId());
        assertThat(guarder.getGuardianStates().keySet(), hasItem(namespacedInstanceId));
        guarder.unregister(namespacedInstanceId.getNamespace(), namespacedInstanceId.getInstanceId());
        assertThat(guarder.getGuardianStates().keySet(), empty());
    }

    @Test
    void start() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        assertThat(guarder.isRunning(), equalTo(false));
        guarder.start();
        assertThat(guarder.isRunning(), equalTo(true));
    }

    @Test
    void stop() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        guarder.start();
        guarder.stop();
        assertThat(guarder.isRunning(), equalTo(false));
    }
}
