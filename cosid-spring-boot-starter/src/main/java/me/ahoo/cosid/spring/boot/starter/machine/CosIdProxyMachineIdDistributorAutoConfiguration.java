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
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.proxy.ProxyMachineLeaseStore;
import me.ahoo.cosid.proxy.api.MachineLeaseClient;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientAuthConfiguration;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientRuntimeHints;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientTimeoutConfiguration;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyHttpInterfaceClients;
import me.ahoo.cosid.spring.boot.starter.ProxyClientCondition;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnMachineStoreType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.client.RestClient;

/**
 * CosId Spring Redis MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnClass(name = "me.ahoo.cosid.proxy.ProxyMachineLeaseStore")
@ConditionalOnMachineStoreType("proxy")
public class CosIdProxyMachineIdDistributorAutoConfiguration {

    /**
     * The machine id lease store through the proxy server's version 2 lease API. Calls are bounded by
     * {@code cosid.proxy.read-timeout}, which must be positive: renewals and releases run under a lock.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInMachineStore
    public ProxyMachineLeaseStore proxyMachineLeaseStore(MachineLeaseClient machineLeaseClient, ObjectProvider<CosIdProperties> cosIdProperties) {
        CosIdProperties properties = cosIdProperties.getIfAvailable(CosIdProperties::new);
        MachineLeaseStores.positive(properties.getProxy().getReadTimeout(), CosId.COSID_PREFIX + "proxy.read-timeout");
        return new ProxyMachineLeaseStore(machineLeaseClient);
    }

    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnBuiltInMachineStore
    public LeaseStoreMachineIdDistributor proxyMachineIdDistributor(ProxyMachineLeaseStore proxyMachineLeaseStore, MachineStateStorage localMachineState,
                                                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new LeaseStoreMachineIdDistributor(proxyMachineLeaseStore, localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The proxy backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdProxyMachineStoreProvider(ObjectProvider<MachineLeaseClient> machineLeaseClient,
                                                               ObjectProvider<CosIdProperties> cosIdProperties) {
        return BuiltInStoreProvider.machine("proxy", context -> proxyMachineIdDistributor(proxyMachineLeaseStore(machineLeaseClient.getObject(), cosIdProperties),
            context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }

    /**
     * The client through CoApi: the default when CoApi is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(ProxyClientCondition.OnCoApi.class)
    @EnableCoApi(clients = MachineLeaseClient.class)
    @Import({CosIdProxyClientTimeoutConfiguration.class, CosIdProxyClientAuthConfiguration.class})
    static class CoApiClient {
    }

    /**
     * The client as a plain bean ({@code cosid.proxy.client=http-interface}, or without CoApi), which Spring AOT
     * processing supports.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(ProxyClientCondition.OnHttpInterface.class)
    @ConditionalOnClass(name = "org.springframework.web.client.RestClient")
    @ImportRuntimeHints(CosIdProxyClientRuntimeHints.class)
    static class HttpInterfaceClient {
        @Bean
        @ConditionalOnMissingBean
        public MachineLeaseClient cosIdProxyMachineLeaseClient(ObjectProvider<CosIdProperties> cosIdProperties, ObjectProvider<RestClient.Builder> restClientBuilder) {
            return CosIdProxyHttpInterfaceClients.create(MachineLeaseClient.class, cosIdProperties, restClientBuilder);
        }
    }
}
