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

package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.coapi.spring.client.reactive.WebClientBuilderCustomizer;
import me.ahoo.coapi.spring.client.sync.RestClientBuilderCustomizer;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * Sends {@link ProxyProperties#getToken()} as {@code Authorization: Bearer <token>} from the CosId proxy clients.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
public class CosIdProxyClientAuthConfiguration {
    
    static String authorization(ObjectProvider<CosIdProperties> cosIdProperties) {
        return CosIdProxyHttpInterfaceClients.authorization(CosIdProxyClientTimeoutConfiguration.proxyProperties(cosIdProperties));
    }
    
    /**
     * Whether the token would cross the network unencrypted: plain {@code http} to a host other than this machine.
     *
     * @param host  {@code cosid.proxy.host}
     * @param token {@code cosid.proxy.token}
     * @return true if the token is sent in clear text over the network
     */
    static boolean sendsTokenInClearText(@Nullable String host, @Nullable String token) {
        return CosIdProxyHttpInterfaceClients.sendsTokenInClearText(host, token);
    }
    
    @Bean
    public RestClientBuilderCustomizer cosIdProxyRestClientAuthCustomizer(ObjectProvider<CosIdProperties> cosIdProperties) {
        String authorization = authorization(cosIdProperties);
        ProxyProperties proxyProperties = CosIdProxyClientTimeoutConfiguration.proxyProperties(cosIdProperties);
        CosIdProxyHttpInterfaceClients.warnIfTokenInClearText(proxyProperties);
        return (coApiDefinition, builder) -> {
            if (authorization != null && CosIdProxyClientTimeoutConfiguration.isProxyClient(coApiDefinition)) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }
    
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
    static class Reactive {
        @Bean
        public WebClientBuilderCustomizer cosIdProxyWebClientAuthCustomizer(ObjectProvider<CosIdProperties> cosIdProperties) {
            String authorization = authorization(cosIdProperties);
            return (coApiDefinition, builder) -> {
                if (authorization != null && CosIdProxyClientTimeoutConfiguration.isProxyClient(coApiDefinition)) {
                    builder.defaultHeader(HttpHeaders.AUTHORIZATION, authorization);
                }
            };
        }
    }
}
