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

package me.ahoo.cosid.proxy.server.configuration;

import me.ahoo.cosid.proxy.server.auth.ProxyAuthProperties;
import me.ahoo.cosid.proxy.server.auth.ProxyAuthWebFilter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Bearer token authentication of the CosId proxy API, see {@link ProxyAuthProperties}.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ProxyAuthProperties.class, ProxyServerProperties.class})
public class ProxyAuthConfiguration {
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ProxyAuthWebFilter proxyAuthWebFilter(ProxyAuthProperties proxyAuthProperties, ProxyServerProperties proxyServerProperties,
                                                 Environment environment) {
        List<String> publicPaths = proxyAuthProperties.getPublicPaths();
        if (publicPaths == null) {
            // Follow a custom actuator base path, so health probes keep working.
            publicPaths = ProxyAuthProperties.healthPaths(environment.getProperty("management.endpoints.web.base-path", "/actuator"));
        }
        ProxyAuthWebFilter filter = new ProxyAuthWebFilter(proxyAuthProperties.getTokens(), proxyAuthProperties.getClients(), publicPaths,
            proxyServerProperties.getNamespaces());
        if (!filter.isEnabled() && !proxyAuthProperties.isAllowAnonymous()) {
            throw new IllegalStateException("No [" + ProxyAuthProperties.PREFIX + ".tokens] or [" + ProxyAuthProperties.PREFIX
                + ".clients] configured: set a token, or set ["
                + ProxyAuthProperties.PREFIX + ".allow-anonymous=true] to run the proxy without authentication (only behind a trusted network boundary).");
        }
        return filter;
    }
}
