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

import java.time.Duration;

/**
 * DefaultMachineIdGuarderTest .
 *
 * @author ahoo wang
 */
class DefaultMachineIdGuarderTest {
    private final ManualMachineIdDistributor distributor = new ManualMachineIdDistributor(1, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);

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

    @Test
    void registerShouldStartLeaseAndGuardShouldRenewIt() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, Duration.ofMinutes(5));
        distributor.distribute(namespace, 10, InstanceId.NONE, Duration.ofMinutes(5));
        guarder.register(namespace, InstanceId.NONE);
        MachineIdLease lease = guarder.getLease(namespace, InstanceId.NONE);
        long registeredUntil = lease.getValidUntil();

        Assertions.assertTrue(lease.isValid(System.currentTimeMillis()));
        guarder.safeGuard();
        assertThat(lease.getValidUntil(), greaterThanOrEqualTo(registeredUntil));
        assertThat(guarder.hasFailure(), equalTo(false));
    }

    @Test
    void guardShouldMarkLeaseLostWhenMachineIdIsLost() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        MachineIdDistributor lostDistributor = new ManualMachineIdDistributor(1, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT) {
            @Override
            public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
                throw new MachineIdLostException(namespace, instanceId, null);
            }
        };
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(lostDistributor, Duration.ofMinutes(5));
        guarder.register(namespace, InstanceId.NONE);

        guarder.safeGuard();

        Assertions.assertTrue(guarder.getLease(namespace, InstanceId.NONE).isLost());
        assertThat(guarder.hasFailure(), equalTo(true));
    }

    @Test
    void unregisteredInstanceHasForeverLease() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, Duration.ofMinutes(5));

        assertThat(guarder.getLease("unknown", InstanceId.NONE), sameInstance(MachineIdLease.FOREVER));
    }
}
