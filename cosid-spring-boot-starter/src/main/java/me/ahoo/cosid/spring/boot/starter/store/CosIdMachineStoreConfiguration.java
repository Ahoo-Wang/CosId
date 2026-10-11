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

package me.ahoo.cosid.spring.boot.starter.store;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

/**
 * Creates the machine id distributor with the {@link CosIdStoreProvider} of the configured store type. Imported by the
 * machine auto-configurations.
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MachineProperties.class)
public class CosIdMachineStoreConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    public MachineIdDistributor machineIdDistributor(MachineProperties machineProperties, ObjectProvider<CosIdStoreProvider> providers,
                                                     MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        MachineProperties.Distributor distributor = machineProperties.getDistributor();
        String type = CosIdStoreProviders.storeType(distributor.getProvider(), distributor.getType());
        MachineStoreContext context = new MachineStoreContext(machineProperties, machineStateStorage, clockBackwardsSynchronizer);
        return CosIdStoreProviders.create(type, providers.orderedStream().collect(Collectors.toList()),
            provider -> provider.createMachineIdDistributor(context), "machine id distributor");
    }
}
