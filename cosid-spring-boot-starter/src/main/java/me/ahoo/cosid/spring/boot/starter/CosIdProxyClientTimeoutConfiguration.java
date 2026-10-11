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

import me.ahoo.coapi.spring.CoApiDefinition;
import me.ahoo.coapi.spring.client.reactive.WebClientBuilderCustomizer;
import me.ahoo.coapi.spring.client.sync.RestClientBuilderCustomizer;
import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.proxy.api.SegmentClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Applies {@link ProxyProperties#getConnectTimeout()} and {@link ProxyProperties#getReadTimeout()} to the CosId proxy
 * clients, which otherwise wait for the proxy server without any timeout.
 *
 * <p>For blocking clients this replaces the request factory of the CosId proxy clients with a JDK {@code HttpClient}
 * one; set both timeouts to zero to keep the application's own. The read timeout also bounds the machine id
 * distribution at startup, so raise it if the proxy server's backend is slow to answer then.</p>
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
public class CosIdProxyClientTimeoutConfiguration {
    
    static boolean isProxyClient(CoApiDefinition coApiDefinition) {
        Class<?> apiType = coApiDefinition.getApiType();
        return MachineClient.class.equals(apiType) || SegmentClient.class.equals(apiType);
    }
    
    static ProxyProperties proxyProperties(ObjectProvider<CosIdProperties> cosIdProperties) {
        CosIdProperties properties = cosIdProperties.getIfAvailable();
        return properties == null ? new ProxyProperties() : properties.getProxy();
    }
    
    static boolean isSet(Duration timeout) {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }
    
    /**
     * Blocking clients (RestClient).
     */
    @Bean
    public RestClientBuilderCustomizer cosIdProxyRestClientTimeoutCustomizer(ObjectProvider<CosIdProperties> cosIdProperties) {
        ProxyProperties proxy = proxyProperties(cosIdProperties);
        return (coApiDefinition, builder) -> {
            // With both timeouts disabled, the application's own request factory is kept.
            if (!isProxyClient(coApiDefinition) || (!isSet(proxy.getConnectTimeout()) && !isSet(proxy.getReadTimeout()))) {
                return;
            }
            // HTTP/1.1 like the other Spring request factories: the JDK client would otherwise try an h2c upgrade.
            HttpClient.Builder httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
            if (isSet(proxy.getConnectTimeout())) {
                httpClientBuilder.connectTimeout(proxy.getConnectTimeout());
            }
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
            if (isSet(proxy.getReadTimeout())) {
                requestFactory.setReadTimeout(proxy.getReadTimeout());
            }
            builder.requestFactory(requestFactory);
        };
    }
    
    /**
     * Reactive clients (WebClient): the whole exchange is bounded by the read timeout.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
    static class Reactive {
        @Bean
        public WebClientBuilderCustomizer cosIdProxyWebClientTimeoutCustomizer(ObjectProvider<CosIdProperties> cosIdProperties) {
            ProxyProperties proxy = proxyProperties(cosIdProperties);
            return (coApiDefinition, builder) -> {
                if (!isProxyClient(coApiDefinition) || !isSet(proxy.getReadTimeout())) {
                    return;
                }
                Duration timeout = proxy.getReadTimeout();
                builder.filter((request, next) -> next.exchange(request).timeout(timeout));
            };
        }
    }
}
