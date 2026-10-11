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
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.zookeeper.ZookeeperMachineIdDistributor;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * CosId Zookeeper MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnProperty(value = MachineProperties.Distributor.TYPE, havingValue = "zookeeper")
@ConditionalOnClass(ZookeeperMachineIdDistributor.class)
public class CosIdZookeeperMachineIdDistributorAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInMachineStore
    public ZookeeperMachineIdDistributor zookeeperMachineIdDistributor(CuratorFramework curatorFramework, RetryPolicy retryPolicy, MachineStateStorage localMachineState,
                                                                       ClockBackwardsSynchronizer clockBackwardsSynchronizer, MachineProperties machineProperties) {
        return new ZookeeperMachineIdDistributor(curatorFramework, retryPolicy, localMachineState, clockBackwardsSynchronizer,
            CallTimeout.of(machineProperties.getDistributor().getZookeeper().getTimeout()));
    }

    /**
     * The ZooKeeper backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdZookeeperMachineStoreProvider(ObjectProvider<CuratorFramework> curatorFramework, ObjectProvider<RetryPolicy> retryPolicy) {
        return BuiltInStoreProvider.machine("zookeeper", context -> zookeeperMachineIdDistributor(curatorFramework.getObject(), retryPolicy.getObject(),
            context.getMachineStateStorage(), context.getClockBackwardsSynchronizer(), context.getMachineProperties()));
    }
}
