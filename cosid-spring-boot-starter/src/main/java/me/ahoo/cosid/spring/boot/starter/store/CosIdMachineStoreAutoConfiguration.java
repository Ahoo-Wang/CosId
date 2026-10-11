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
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdJdbcMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMongoMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdProxyMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdSpringRedisMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdZookeeperMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.stream.Collectors;

/**
 * Creates the machine id distributor with the {@link CosIdStoreProvider} of the configured store type, when no
 * built-in backend bean was created: the application selected a provider of its own, or no backend of the type is on
 * the classpath (then startup fails naming the type).
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@AutoConfiguration(after = {
    CosIdMachineAutoConfiguration.class,
    CosIdJdbcMachineIdDistributorAutoConfiguration.class,
    CosIdMongoMachineIdDistributorAutoConfiguration.class,
    CosIdProxyMachineIdDistributorAutoConfiguration.class,
    CosIdSpringRedisMachineIdDistributorAutoConfiguration.class,
    CosIdZookeeperMachineIdDistributorAutoConfiguration.class
})
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@EnableConfigurationProperties(MachineProperties.class)
public class CosIdMachineStoreAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    public MachineIdDistributor cosIdMachineIdDistributor(MachineProperties machineProperties, ObjectProvider<CosIdStoreProvider> providers,
                                                          MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        MachineProperties.Distributor distributor = machineProperties.getDistributor();
        String type = CosIdStoreProviders.storeType(distributor.getProvider(), distributor.getType());
        MachineStoreContext context = new MachineStoreContext(machineProperties, machineStateStorage, clockBackwardsSynchronizer);
        return CosIdStoreProviders.create(type, providers.orderedStream().collect(Collectors.toList()),
            provider -> provider.createMachineIdDistributor(context), "machine id distributor");
    }
}
