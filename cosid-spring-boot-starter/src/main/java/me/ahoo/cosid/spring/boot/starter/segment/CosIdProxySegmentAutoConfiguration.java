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

package me.ahoo.cosid.spring.boot.starter.segment;

import me.ahoo.coapi.spring.EnableCoApi;
import me.ahoo.cosid.proxy.ProxyIdSegmentDistributorFactory;
import me.ahoo.cosid.proxy.api.SegmentClient;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientAuthConfiguration;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientRuntimeHints;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyClientTimeoutConfiguration;
import me.ahoo.cosid.spring.boot.starter.CosIdProxyHttpInterfaceClients;
import me.ahoo.cosid.spring.boot.starter.ProxyClientCondition;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInSegmentStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnSegmentStoreType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.client.RestClient;

/**
 * CosId Jdbc Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass(name = "me.ahoo.cosid.proxy.ProxyIdSegmentDistributorFactory")
@ConditionalOnSegmentStoreType("proxy")
public class CosIdProxySegmentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInSegmentStore
    public IdSegmentDistributorFactory idSegmentDistributorFactory(SegmentClient segmentClient) {
        return new ProxyIdSegmentDistributorFactory(segmentClient);
    }

    /**
     * The proxy backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdProxySegmentStoreProvider(ObjectProvider<SegmentClient> segmentClient) {
        return BuiltInStoreProvider.segment("proxy", context -> idSegmentDistributorFactory(segmentClient.getObject()));
    }

    /**
     * The client through CoApi: the default when CoApi is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(ProxyClientCondition.OnCoApi.class)
    @EnableCoApi(clients = SegmentClient.class)
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
        public SegmentClient cosIdProxySegmentClient(ObjectProvider<CosIdProperties> cosIdProperties, ObjectProvider<RestClient.Builder> restClientBuilder) {
            return CosIdProxyHttpInterfaceClients.create(SegmentClient.class, cosIdProperties, restClientBuilder);
        }
    }
}
