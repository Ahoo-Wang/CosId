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

import com.google.common.base.Preconditions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

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
    
    private final CosIdProperties cosIdProperties;
    private final SegmentIdProperties segmentIdProperties;
    
    public CosIdSegmentAutoConfiguration(CosIdProperties cosIdProperties, SegmentIdProperties segmentIdProperties) {
        this.cosIdProperties = cosIdProperties;
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
    
    @Bean
    public SegmentIdBeanRegistrar segmentIdBeanRegistrar(IdSegmentDistributorFactory distributorFactory,
                                                       IdGeneratorProvider idGeneratorProvider,
                                                       PrefetchWorkerExecutorService prefetchWorkerExecutorService,
                                                       ConfigurableApplicationContext applicationContext) {
        
        return new SegmentIdBeanRegistrar(cosIdProperties,
            segmentIdProperties,
            distributorFactory,
            idGeneratorProvider,
            prefetchWorkerExecutorService,
            applicationContext);
    }
}
