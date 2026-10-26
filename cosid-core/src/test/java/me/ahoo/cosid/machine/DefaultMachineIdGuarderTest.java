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
import java.util.concurrent.ScheduledExecutorService;

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
    void stopShutsDownTheExecutorItCreated() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        guarder.start();
        ScheduledExecutorService executorService = guarder.getExecutorService();
        guarder.stop();
        assertThat(executorService.isShutdown(), equalTo(true));
    }

    @Test
    void startAfterStopRunsOnANewExecutorItCreated() {
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, Duration.ofMillis(10), Duration.ofMillis(10),
            MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION, Duration.ZERO);
        guarder.start();
        guarder.stop();
        guarder.start();
        try {
            assertThat(guarder.isRunning(), equalTo(true));
            assertThat(guarder.getExecutorService().isShutdown(), equalTo(false));
        } finally {
            guarder.stop();
        }
    }

    @Test
    void stopLeavesAnInjectedExecutorRunning() {
        ScheduledExecutorService executorService = DefaultMachineIdGuarder.executorService();
        try {
            DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, executorService, Duration.ofMinutes(1), Duration.ofMinutes(1),
                MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
            guarder.start();
            guarder.stop();
            assertThat(executorService.isShutdown(), equalTo(false));
            guarder.start();
            assertThat(guarder.isRunning(), equalTo(true));
            guarder.stop();
        } finally {
            executorService.shutdownNow();
        }
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

    @Test
    void localStateShouldBeRefreshedBetweenGuards() throws InterruptedException {
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        ManualMachineIdDistributor localDistributor = new ManualMachineIdDistributor(1, storage, ClockBackwardsSynchronizer.DEFAULT);
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("watermark", false);
        localDistributor.distribute(namespace, 10, instanceId, Duration.ofMinutes(5));
        long distributedAt = storage.get(namespace, instanceId).getLastTimeStamp();
        // The remote guard does not run during the test: only the local refresh does.
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(localDistributor, DefaultMachineIdGuarder.executorService(),
            Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofMillis(20));
        guarder.register(namespace, instanceId);
        
        guarder.start();
        try {
            Thread.sleep(200);
            assertThat(storage.get(namespace, instanceId).getLastTimeStamp(), greaterThan(distributedAt));
        } finally {
            guarder.stop();
        }
    }
    
    @Test
    void localStateRefreshCanBeDisabled() throws InterruptedException {
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        ManualMachineIdDistributor localDistributor = new ManualMachineIdDistributor(1, storage, ClockBackwardsSynchronizer.DEFAULT);
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("watermark", false);
        localDistributor.distribute(namespace, 10, instanceId, Duration.ofMinutes(5));
        long distributedAt = storage.get(namespace, instanceId).getLastTimeStamp();
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(localDistributor, DefaultMachineIdGuarder.executorService(),
            Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(5), Duration.ZERO);
        guarder.register(namespace, instanceId);
        
        guarder.start();
        try {
            Thread.sleep(100);
            assertThat(storage.get(namespace, instanceId).getLastTimeStamp(), equalTo(distributedAt));
        } finally {
            guarder.stop();
        }
    }

    @Test
    void localStateShouldBeRefreshedWhileRemoteGuardHangs() throws InterruptedException {
        InMemoryMachineStateStorage storage = new InMemoryMachineStateStorage();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        ManualMachineIdDistributor hangingDistributor = new ManualMachineIdDistributor(1, storage, ClockBackwardsSynchronizer.DEFAULT) {
            @Override
            protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
                try {
                    release.await();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        InstanceId instanceId = InstanceId.of("watermark", false);
        hangingDistributor.distribute(namespace, 10, instanceId, Duration.ofMinutes(5));
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(hangingDistributor, DefaultMachineIdGuarder.executorService(),
            Duration.ZERO, Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofMillis(20));
        guarder.register(namespace, instanceId);
        
        guarder.start();
        try {
            Thread.sleep(100);
            long afterGuardStarted = storage.get(namespace, instanceId).getLastTimeStamp();
            Thread.sleep(200);
            assertThat(storage.get(namespace, instanceId).getLastTimeStamp(), greaterThan(afterGuardStarted));
        } finally {
            release.countDown();
            guarder.stop();
        }
    }
}
