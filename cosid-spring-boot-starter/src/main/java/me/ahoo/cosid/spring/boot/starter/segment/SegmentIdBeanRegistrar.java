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
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.segment.grouped.DateGroupedSupplier;
import me.ahoo.cosid.segment.grouped.GroupedIdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.spring.boot.starter.Namespaces;

import com.google.common.base.MoreObjects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class SegmentIdBeanRegistrar implements InitializingBean {
    private final CosIdProperties cosIdProperties;
    private final SegmentIdProperties segmentIdProperties;
    private final IdSegmentDistributorFactory distributorFactory;
    private final IdGeneratorProvider idGeneratorProvider;
    private final PrefetchWorkerExecutorService prefetchWorkerExecutorService;
    private final ConfigurableApplicationContext applicationContext;
    
    public SegmentIdBeanRegistrar(CosIdProperties cosIdProperties,
                                  SegmentIdProperties segmentIdProperties,
                                  IdSegmentDistributorFactory distributorFactory,
                                  IdGeneratorProvider idGeneratorProvider,
                                  PrefetchWorkerExecutorService prefetchWorkerExecutorService,
                                  ConfigurableApplicationContext applicationContext) {
        this.cosIdProperties = cosIdProperties;
        this.segmentIdProperties = segmentIdProperties;
        this.distributorFactory = distributorFactory;
        this.idGeneratorProvider = idGeneratorProvider;
        this.prefetchWorkerExecutorService = prefetchWorkerExecutorService;
        this.applicationContext = applicationContext;
    }
    
    @Override
    public void afterPropertiesSet() {
        register();
    }
    
    public void register() {
        SegmentIdProperties.ShardIdDefinition shareIdDefinition = segmentIdProperties.getShare();
        if (shareIdDefinition.isEnabled()) {
            registerIdDefinition(IdGeneratorProvider.SHARE, shareIdDefinition);
        }
        segmentIdProperties.getProvider().forEach(this::registerIdDefinition);
    }
    
    private void registerIdDefinition(String name, SegmentIdProperties.IdDefinition idDefinition) {
        IdSegmentDistributorDefinition distributorDefinition = asDistributorDefinition(name, idDefinition);
        IdSegmentDistributor idSegmentDistributor;
        if (idDefinition.getGrouped() == SegmentIdProperties.IdDefinition.Grouped.YEAR) {
            idSegmentDistributor = new GroupedIdSegmentDistributorFactory(DateGroupedSupplier.YEAR, distributorFactory).create(distributorDefinition);
        } else {
            idSegmentDistributor = distributorFactory.create(distributorDefinition);
        }
        
        SegmentId idGenerator = createSegment(segmentIdProperties, idDefinition, idSegmentDistributor, prefetchWorkerExecutorService);
        registerSegmentId(name, idGenerator);
    }
    
    private void registerSegmentId(String name, SegmentId segmentId) {
        if (!idGeneratorProvider.get(name).isPresent()) {
            idGeneratorProvider.set(name, segmentId);
        }
        
        String beanName = name + "SegmentId";
        applicationContext.getBeanFactory().registerSingleton(beanName, segmentId);
    }
    
    private IdSegmentDistributorDefinition asDistributorDefinition(String name, SegmentIdProperties.IdDefinition idDefinition) {
        String namespace = Namespaces.firstNotBlank(idDefinition.getNamespace(), cosIdProperties.getNamespace());
        return new IdSegmentDistributorDefinition(namespace, name, idDefinition.getOffset(), idDefinition.getStep());
    }
    
    private static SegmentId createSegment(SegmentIdProperties segmentIdProperties, SegmentIdProperties.IdDefinition idDefinition, IdSegmentDistributor idSegmentDistributor,
                                           PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        long ttl = MoreObjects.firstNonNull(idDefinition.getTtl(), segmentIdProperties.getTtl());
        SegmentIdProperties.Mode mode = MoreObjects.firstNonNull(idDefinition.getMode(), segmentIdProperties.getMode());
        
        SegmentId segmentId;
        if (SegmentIdProperties.Mode.SEGMENT.equals(mode)) {
            segmentId = new DefaultSegmentId(ttl, idSegmentDistributor);
        } else {
            SegmentIdProperties.Chain chain = MoreObjects.firstNonNull(idDefinition.getChain(), segmentIdProperties.getChain());
            segmentId = new SegmentChainId(ttl, chain.getSafeDistance(), idSegmentDistributor, prefetchWorkerExecutorService);
        }
        
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        return new SegmentIdConverterDecorator(segmentId, converterDefinition).decorate();
    }
    
}
