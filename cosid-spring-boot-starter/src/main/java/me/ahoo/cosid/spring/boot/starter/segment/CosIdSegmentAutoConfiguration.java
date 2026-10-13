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

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.metrics.CosIdMetricsProperties;

import com.google.common.base.Preconditions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * CosId Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
public class CosIdSegmentAutoConfiguration {
    
    private final SegmentIdProperties segmentIdProperties;
    
    public CosIdSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public PrefetchWorkerExecutorService prefetchWorkerExecutorService() {
        SegmentIdProperties.Chain.PrefetchWorker prefetchWorker = segmentIdProperties.getChain().getPrefetchWorker();
        Preconditions.checkNotNull(prefetchWorker, "cosid.segment.chain.prefetch-worker can not be null!");
        return new PrefetchWorkerExecutorService(prefetchWorker.getPrefetchPeriod(), prefetchWorker.getCorePoolSize(), prefetchWorker.isShutdownHook());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdLifecyclePrefetchWorkerExecutorService lifecycleSegmentChainId(PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        return new CosIdLifecyclePrefetchWorkerExecutorService(prefetchWorkerExecutorService);
    }
    
    /**
     * Creates the segment ids, after running the {@link CustomizeSegmentIdProperties}.
     */
    @Bean(SegmentIdBeanRegistrar.BEAN_NAME)
    public SegmentIdBeanRegistrar segmentIdBeanRegistrar(CosIdProperties cosIdProperties,
                                                         IdSegmentDistributorFactory distributorFactory,
                                                         IdGeneratorProvider idGeneratorProvider,
                                                         PrefetchWorkerExecutorService prefetchWorkerExecutorService,
                                                         ObjectProvider<CustomizeSegmentIdProperties> customizers,
                                                         ConfigurableListableBeanFactory beanFactory) {
        SegmentIdBeanRegistrar registrar = new SegmentIdBeanRegistrar(cosIdProperties, segmentIdProperties, distributorFactory, idGeneratorProvider,
            prefetchWorkerExecutorService, customizers, (BeanDefinitionRegistry) beanFactory);
        registrar.setCountGenerated(CosIdMetricsProperties.isCountGenerated(beanFactory.getBean(Environment.class)));
        return registrar;
    }
    
    /**
     * The segment id beans, as bean definitions: see {@link SegmentIdBeanDefinitionRegistrar}.
     */
    @Bean
    public static SegmentIdBeanDefinitionRegistrar segmentIdBeanDefinitionRegistrar() {
        return new SegmentIdBeanDefinitionRegistrar();
    }
}
