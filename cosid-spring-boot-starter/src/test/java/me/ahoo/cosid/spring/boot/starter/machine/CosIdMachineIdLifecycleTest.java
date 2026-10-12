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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import me.ahoo.cosid.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class CosIdMachineIdLifecycleTest {
    
    @Test
    void stopShouldRevertDistributedMachineIdsWhenGuarderIsDisabled() {
        RecordingDistributor distributor = new RecordingDistributor();
        GuardDistribute guardDistribute = new GuardDistribute(distributor, MachineIdGuarder.NONE);
        guardDistribute.distribute("ns", 10, InstanceId.of("instance", false), Duration.ofMinutes(1));
        CosIdMachineIdLifecycle lifecycle = new CosIdMachineIdLifecycle(MachineIdGuarder.NONE, distributor, guardDistribute);
        
        lifecycle.start();
        lifecycle.stop();
        
        assertThat(distributor.reverted, contains("ns:instance"));
    }
    
    @Test
    void stopShouldRevertRemainingMachineIdsWhenOneRevertFails() {
        RecordingDistributor distributor = new RecordingDistributor();
        distributor.failingInstance = "first";
        GuardDistribute guardDistribute = new GuardDistribute(distributor, MachineIdGuarder.NONE);
        guardDistribute.distribute("ns", 10, InstanceId.of("first", false), Duration.ofMinutes(1));
        guardDistribute.distribute("ns", 10, InstanceId.of("second", false), Duration.ofMinutes(1));
        CosIdMachineIdLifecycle lifecycle = new CosIdMachineIdLifecycle(MachineIdGuarder.NONE, distributor, guardDistribute);
        
        lifecycle.start();
        lifecycle.stop();
        
        assertThat(distributor.reverted, containsInAnyOrder("ns:first", "ns:second"));
    }
    
    @Test
    void stopShouldReleaseLeaseBeforeRevert() {
        RecordingDistributor distributor = new RecordingDistributor();
        DefaultMachineIdGuarder guarder = new DefaultMachineIdGuarder(distributor, Duration.ofMinutes(1));
        GuardDistribute guardDistribute = new GuardDistribute(distributor, guarder);
        InstanceId instanceId = InstanceId.of("instance", false);
        guardDistribute.distribute("ns", 10, instanceId, Duration.ofMinutes(1));
        MachineIdLease lease = guardDistribute.getLease("ns", instanceId);
        distributor.onRevert = () -> distributor.releasedAtRevert.add(lease.isReleased());
        CosIdMachineIdLifecycle lifecycle = new CosIdMachineIdLifecycle(guarder, distributor, guardDistribute);
        
        lifecycle.start();
        lifecycle.stop();
        
        assertThat(distributor.releasedAtRevert, contains(true));
        assertThat(lease.isValid(System.currentTimeMillis()), equalTo(false));
    }
    
    @Test
    void phaseShouldStopAfterWebServerGracefulShutdown() {
        CosIdMachineIdLifecycle lifecycle = new CosIdMachineIdLifecycle(MachineIdGuarder.NONE, new RecordingDistributor(), null);
        
        assertThat(lifecycle.getPhase(), lessThan(WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 1024));
    }
    
    private ApplicationContextRunner contextRunner(RecordingDistributor distributor) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMachineAutoConfiguration.class))
            .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
            .withBean(MachineIdDistributor.class, () -> distributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.PREFIX + ".instance-id=worker-a");
    }
    
    @Test
    void failedStartupRevertsTheDistributedMachineId() {
        RecordingDistributor distributor = new RecordingDistributor();
        contextRunner(distributor).withUserConfiguration(FailingAfterMachineId.class).run(context -> {
            assertThat(context.getStartupFailure(), notNullValue());
            assertThat(distributor.reverted, contains("cosid:worker-a"));
        });
    }
    
    @Test
    void shutdownRevertsTheMachineIdOnce() {
        RecordingDistributor distributor = new RecordingDistributor();
        contextRunner(distributor).run(context -> {
            assertThat(context.getStartupFailure(), nullValue());
            context.getBean(MachineId.class);
            assertThat(distributor.reverted, empty());
        });
        assertThat(distributor.reverted, contains("cosid:worker-a"));
    }
    
    @Configuration(proxyBeanMethods = false)
    static class FailingAfterMachineId {
        @Bean
        String failingBean(MachineId machineId) {
            throw new IllegalStateException("fails after the machine id was distributed: " + machineId.getMachineId());
        }
    }
    
    static class RecordingDistributor implements MachineIdDistributor {
        final List<String> reverted = new ArrayList<>();
        final List<Boolean> releasedAtRevert = new ArrayList<>();
        String failingInstance;
        Runnable onRevert = () -> { };
        
        @Override
        public @NonNull MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            return MachineState.of(1);
        }
        
        @Override
        public void revert(String namespace, InstanceId instanceId) {
            reverted.add(namespace + ":" + instanceId.getInstanceId());
            onRevert.run();
            if (instanceId.getInstanceId().equals(failingInstance)) {
                throw new IllegalStateException("revert failed");
            }
        }
        
        @Override
        public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) {
        }
    }
}
