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

import me.ahoo.coapi.spring.EnableCoApi;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientAuthConfiguration;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientTimeoutConfiguration;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.CosIdMachineStoreConfiguration;
import me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * CosId Spring Redis MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnProperty(value = MachineProperties.Distributor.TYPE, havingValue = "proxy")
@EnableCoApi(clients = MachineClient.class)
@Import({CosIdMachineStoreConfiguration.class, CosIdProxyClientTimeoutConfiguration.class, CosIdProxyClientAuthConfiguration.class})
public class CosIdProxyMachineIdDistributorAutoConfiguration {

    @Bean
    public CosIdStoreProvider cosIdProxyMachineStoreProvider(ObjectProvider<MachineClient> machineClient) {
        return BuiltInStoreProvider.machine("proxy",
            context -> new ProxyMachineIdDistributor(machineClient.getObject(), context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }


}
