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

import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.LocalHostAddressSupplier;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration(afterName = "org.springframework.cloud.commons.util.UtilAutoConfiguration")
@ConditionalOnCosIdEnabled
public class CosIdHostNameAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public HostAddressSupplier hostNameSupplier() {
        return LocalHostAddressSupplier.INSTANCE;
    }
    
    @Configuration
    @ConditionalOnClass(InetUtils.class)
    static class CloudUtilInstanceIdConfiguration {
        @Bean
        @ConditionalOnBean(InetUtils.class)
        public HostAddressSupplier hostNameSupplier(InetUtils inetUtils) {
            return new CloudUtilHostAddressSupplier(inetUtils);
        }
    }
    
    static class CloudUtilHostAddressSupplier implements HostAddressSupplier {
        private final InetUtils inetUtils;
        
        CloudUtilHostAddressSupplier(InetUtils inetUtils) {
            this.inetUtils = inetUtils;
        }
        
        @Override
        public String getHostAddress() {
            return inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
        }
    }
}
