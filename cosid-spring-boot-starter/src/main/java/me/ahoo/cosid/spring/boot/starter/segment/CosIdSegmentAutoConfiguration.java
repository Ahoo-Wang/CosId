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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.*;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
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
        return new PrefetchWorkerExecutorService(prefetchWorker.getPrefetchPeriod(), prefetchWorker.getCorePoolSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public CosIdLifecyclePrefetchWorkerExecutorService lifecycleSegmentChainId(PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        return new CosIdLifecyclePrefetchWorkerExecutorService(prefetchWorkerExecutorService);
    }

    private IdSegmentDistributorDefinition asDistributorDefinition(String name, SegmentIdProperties.IdDefinition idDefinition) {
        return new IdSegmentDistributorDefinition(cosIdProperties.getNamespace(), name, idDefinition.getOffset(), idDefinition.getStep());
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentId shareSegmentId(IdSegmentDistributorFactory distributorFactory, IdGeneratorProvider idGeneratorProvider, PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        SegmentIdProperties.IdDefinition shareIdDefinition = segmentIdProperties.getShare();
        IdSegmentDistributorDefinition shareDistributorDefinition = asDistributorDefinition(IdGeneratorProvider.SHARE, shareIdDefinition);
        IdSegmentDistributor shareIdSegmentDistributor = distributorFactory.create(shareDistributorDefinition);

        SegmentId shareIdGen = createSegment(segmentIdProperties, shareIdDefinition, shareIdSegmentDistributor, prefetchWorkerExecutorService);

        if (Objects.isNull(idGeneratorProvider.getShare())) {
            idGeneratorProvider.setShare(shareIdGen);
        }
        if (Objects.isNull(segmentIdProperties.getProvider())) {
            return shareIdGen;
        }

        segmentIdProperties.getProvider().forEach((name, idDefinition) -> {
            IdSegmentDistributorDefinition distributorDefinition = asDistributorDefinition(name, shareIdDefinition);
            IdSegmentDistributor idSegmentDistributor = distributorFactory.create(distributorDefinition);
            SegmentId idGenerator = createSegment(segmentIdProperties, idDefinition, idSegmentDistributor, prefetchWorkerExecutorService);
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }


    private static SegmentId createSegment(SegmentIdProperties segmentIdProperties, SegmentIdProperties.IdDefinition idDefinition, IdSegmentDistributor idSegmentDistributor, PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        long ttl = MoreObjects.firstNonNull(idDefinition.getTtl(), segmentIdProperties.getTtl());
        SegmentIdProperties.Mode mode = MoreObjects.firstNonNull(idDefinition.getMode(), segmentIdProperties.getMode());

        SegmentId segmentId;
        if (SegmentIdProperties.Mode.DEFAULT.equals(mode)) {
            segmentId = new DefaultSegmentId(ttl, idSegmentDistributor);
        } else {
            SegmentIdProperties.Chain chain = MoreObjects.firstNonNull(idDefinition.getChain(), segmentIdProperties.getChain());
            segmentId = new SegmentChainId(ttl, chain.getSafeDistance(), idSegmentDistributor, prefetchWorkerExecutorService);
        }

        IdConverterDefinition converterDefinition = idDefinition.getConverter();

        if (Objects.isNull(converterDefinition)) {
            return segmentId;
        }

        IdConverter idConverter = ToStringIdConverter.INSTANCE;
        switch (converterDefinition.getType()) {
            case TO_STRING: {
                break;
            }
            case RADIX: {
                IdConverterDefinition.Radix radix = converterDefinition.getRadix();
                idConverter = new Radix62IdConverter(radix.isPadStart(), radix.getCharSize());
                break;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + converterDefinition.getType());
        }

        if (!PrefixIdConverter.EMPTY_PREFIX.equals(converterDefinition.getPrefix())) {
            idConverter = new PrefixIdConverter(converterDefinition.getPrefix(), idConverter);
        }

        return new StringSegmentId(segmentId, idConverter);

    }
}
