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

package me.ahoo.cosid.spring.boot.starter.store;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdJdbcSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdMongoSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdProxySegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSpringRedisSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdZookeeperSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.stream.Collectors;

/**
 * Creates the id segment distributor factory with the {@link CosIdStoreProvider} of the configured store type, when no
 * built-in backend bean was created: the application selected a provider of its own, or no backend of the type is on
 * the classpath (then startup fails naming the type).
 *
 * @author ahoo wang
 * @since 3.4.0
 */
@AutoConfiguration(after = {
    CosIdJdbcSegmentAutoConfiguration.class,
    CosIdMongoSegmentAutoConfiguration.class,
    CosIdProxySegmentAutoConfiguration.class,
    CosIdSpringRedisSegmentAutoConfiguration.class,
    CosIdZookeeperSegmentAutoConfiguration.class
})
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
public class CosIdSegmentStoreAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(IdSegmentDistributorFactory.class)
    public IdSegmentDistributorFactory cosIdIdSegmentDistributorFactory(SegmentIdProperties segmentIdProperties, ObjectProvider<CosIdStoreProvider> providers) {
        SegmentIdProperties.Distributor distributor = segmentIdProperties.getDistributor();
        String type = CosIdStoreProviders.storeType(distributor.getProvider(), distributor.getType());
        SegmentStoreContext context = new SegmentStoreContext(segmentIdProperties);
        return CosIdStoreProviders.create(type, providers.orderedStream().collect(Collectors.toList()),
            provider -> provider.createIdSegmentDistributorFactory(context), "id segment distributor factory");
    }
    
    @Bean
    static SmartInitializingSingleton cosIdIgnoredSegmentStoreProviderWarning(ListableBeanFactory beanFactory, ObjectProvider<SegmentIdProperties> segmentIdProperties) {
        return new IgnoredStoreProviderWarning(beanFactory, "id segment distributor factory", IdSegmentDistributorFactory.class, "cosIdIdSegmentDistributorFactory", () -> {
            SegmentIdProperties.Distributor distributor = segmentIdProperties.getObject().getDistributor();
            return CosIdStoreProviders.storeType(distributor.getProvider(), distributor.getType());
        });
    }
}
