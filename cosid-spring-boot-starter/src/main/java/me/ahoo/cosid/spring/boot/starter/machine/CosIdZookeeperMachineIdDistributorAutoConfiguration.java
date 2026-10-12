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
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnMachineStoreType;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.zookeeper.ZookeeperMachineLeaseStore;

import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * CosId Zookeeper MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnMachineStoreType("zookeeper")
@ConditionalOnClass(ZookeeperMachineLeaseStore.class)
public class CosIdZookeeperMachineIdDistributorAutoConfiguration {
    
    /**
     * The machine id lease store on ZooKeeper; every call is bounded by {@code cosid.machine.distributor.zookeeper.timeout}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInMachineStore
    public ZookeeperMachineLeaseStore zookeeperMachineLeaseStore(CuratorFramework curatorFramework, MachineProperties machineProperties) {
        return new ZookeeperMachineLeaseStore(curatorFramework, CallTimeout.of(MachineLeaseStores.positive(
            machineProperties.getDistributor().getZookeeper().getTimeout(), MachineProperties.PREFIX + ".distributor.zookeeper.timeout")));
    }
    
    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnBuiltInMachineStore
    public LeaseStoreMachineIdDistributor zookeeperMachineIdDistributor(ZookeeperMachineLeaseStore zookeeperMachineLeaseStore, MachineStateStorage localMachineState,
                                                                        ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new LeaseStoreMachineIdDistributor(zookeeperMachineLeaseStore, localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The ZooKeeper backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdZookeeperMachineStoreProvider(ObjectProvider<CuratorFramework> curatorFramework) {
        return BuiltInStoreProvider.machine("zookeeper", context -> zookeeperMachineIdDistributor(
            zookeeperMachineLeaseStore(curatorFramework.getObject(), context.getMachineProperties()),
            context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }
}
