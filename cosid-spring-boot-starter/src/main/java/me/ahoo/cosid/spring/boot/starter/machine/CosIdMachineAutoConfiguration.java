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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.LocalMachineStateStorage;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.util.ProcessId;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Objects;

@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@EnableConfigurationProperties(MachineProperties.class)
public class CosIdMachineAutoConfiguration {
    private final CosIdProperties cosIdProperties;
    private final MachineProperties machineProperties;
    
    public CosIdMachineAutoConfiguration(CosIdProperties cosIdProperties, MachineProperties machineProperties) {
        this.cosIdProperties = cosIdProperties;
        this.machineProperties = machineProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public InstanceId instanceId(HostAddressSupplier hostAddressSupplier) {
        
        boolean stable = Boolean.TRUE.equals(machineProperties.getStable());
        
        if (!Strings.isNullOrEmpty(machineProperties.getInstanceId())) {
            return InstanceId.of(machineProperties.getInstanceId(), stable);
        }
        
        int port = ProcessId.CURRENT.getProcessId();
        if (Objects.nonNull(machineProperties.getPort()) && machineProperties.getPort() > 0) {
            port = machineProperties.getPort();
        }
        
        return InstanceId.of(hostAddressSupplier.getHostAddress(), port, stable);
    }
    
    @Bean
    @ConditionalOnMissingBean(value = MachineId.class)
    public MachineId machineId(MachineIdDistributor machineIdDistributor, InstanceId instanceId) {
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), machineProperties.getMachineBit(), instanceId, machineProperties.getSafeGuardDuration()).getMachineId();
        return new MachineId(machineId);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MachineStateStorage machineStateStorage() {
        if (Boolean.TRUE.equals(machineProperties.getStable())) {
            return new LocalMachineStateStorage(machineProperties.getStateStorage().getLocal().getStateLocation());
        }
        return MachineStateStorage.IN_MEMORY;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ClockBackwardsSynchronizer clockBackwardsSynchronizer() {
        MachineProperties.ClockBackwards clockBackwards = machineProperties.getClockBackwards();
        Preconditions.checkNotNull(clockBackwards, "cosid.machine.clockBackwards can not be null.");
        return new DefaultClockBackwardsSynchronizer(clockBackwards.getSpinThreshold(), clockBackwards.getBrokenThreshold());
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = MachineProperties.Distributor.TYPE, matchIfMissing = true, havingValue = "manual")
    public ManualMachineIdDistributor machineIdDistributor(MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        MachineProperties.Manual manual = machineProperties.getDistributor().getManual();
        Preconditions.checkNotNull(manual, "cosid.machine.distributor.manual can not be null.");
        Integer machineId = manual.getMachineId();
        Preconditions.checkNotNull(machineId, "cosid.machine.distributor.manual.machineId can not be null.");
        Preconditions.checkArgument(machineId >= 0, "cosid.machine.distributor.manual.machineId can not be less than 0.");
        return new ManualMachineIdDistributor(machineId, localMachineState, clockBackwardsSynchronizer);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = MachineProperties.Distributor.TYPE, havingValue = "stateful_set")
    public StatefulSetMachineIdDistributor statefulSetMachineIdDistributor(MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new StatefulSetMachineIdDistributor(localMachineState, clockBackwardsSynchronizer);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdLifecycleMachineIdDistributor cosIdLifecycleMachineIdDistributor(InstanceId instanceId, MachineIdDistributor machineIdDistributor) {
        return new CosIdLifecycleMachineIdDistributor(cosIdProperties, instanceId, machineIdDistributor);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MachineIdGuarder machineIdGuarder(MachineIdDistributor machineIdDistributor) {
        MachineProperties.Guarder guarder = machineProperties.getGuarder();
        if (!guarder.isEnabled()) {
            return MachineIdGuarder.NONE;
        }
        return new DefaultMachineIdGuarder(machineIdDistributor, DefaultMachineIdGuarder.executorService(), guarder.getInitialDelay(), guarder.getDelay(), machineProperties.getSafeGuardDuration());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdLifecycleMachineIdGuarder cosIdLifecycleMachineIdGuarder(InstanceId instanceId, MachineIdGuarder machineIdGuarder) {
        return new CosIdLifecycleMachineIdGuarder(cosIdProperties, instanceId, machineIdGuarder);
    }
    
}
