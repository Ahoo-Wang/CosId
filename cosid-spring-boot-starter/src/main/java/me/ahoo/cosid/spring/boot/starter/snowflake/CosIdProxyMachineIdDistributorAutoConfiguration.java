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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.proxy.ProxyMachineIdDistributor;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;

import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CosId Spring Redis MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSnowflakeEnabled
@ConditionalOnProperty(value = SnowflakeIdProperties.Machine.Distributor.TYPE, havingValue = "proxy")
public class CosIdProxyMachineIdDistributorAutoConfiguration {
    private final CosIdProperties cosIdProperties;
    
    public CosIdProxyMachineIdDistributorAutoConfiguration(CosIdProperties cosIdProperties) {
        this.cosIdProperties = cosIdProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder().build();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ProxyMachineIdDistributor proxyMachineIdDistributor(OkHttpClient httpClient, MachineStateStorage localMachineState,
                                                               ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new ProxyMachineIdDistributor(httpClient, cosIdProperties.getProxy().getHost(), localMachineState, clockBackwardsSynchronizer);
    }
}
