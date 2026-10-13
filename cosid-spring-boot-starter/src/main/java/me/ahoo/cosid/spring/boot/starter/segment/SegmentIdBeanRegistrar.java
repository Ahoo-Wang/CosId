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
import me.ahoo.cosid.segment.CountedSegmentId;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.segment.grouped.GroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupedIdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.date.YearGroupBySupplier;
import me.ahoo.cosid.segment.grouped.date.YearMonthDayGroupBySupplier;
import me.ahoo.cosid.segment.grouped.date.YearMonthGroupBySupplier;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.spring.boot.starter.Namespaces;
import me.ahoo.cosid.util.CallTimeout;

import com.google.common.base.MoreObjects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SegmentIdBeanRegistrar implements InitializingBean, DisposableBean {
    private volatile boolean countGenerated;
    
    /**
     * The bean name, the factory bean of the {@code {name}SegmentId} bean definitions.
     */
    public static final String BEAN_NAME = "segmentIdBeanRegistrar";
    private final CosIdProperties cosIdProperties;
    private final SegmentIdProperties segmentIdProperties;
    private final IdSegmentDistributorFactory distributorFactory;
    private final IdGeneratorProvider idGeneratorProvider;
    private final PrefetchWorkerExecutorService prefetchWorkerExecutorService;
    @Nullable
    private final BeanDefinitionRegistry beanDefinitions;
    private final Map<String, SegmentId> created = new ConcurrentHashMap<>();
    private final Set<String> providerOnly = ConcurrentHashMap.newKeySet();

    /**
     * Create the factory of the segment ids: runs every {@link CustomizeSegmentIdProperties} on the properties first.
     *
     * @param beanDefinitions the registry of the {@code {name}SegmentId} bean definitions, {@code null} for none
     */
    public SegmentIdBeanRegistrar(CosIdProperties cosIdProperties,
                                  SegmentIdProperties segmentIdProperties,
                                  IdSegmentDistributorFactory distributorFactory,
                                  IdGeneratorProvider idGeneratorProvider,
                                  PrefetchWorkerExecutorService prefetchWorkerExecutorService,
                                  ObjectProvider<CustomizeSegmentIdProperties> customizers,
                                  @Nullable BeanDefinitionRegistry beanDefinitions) {
        this.cosIdProperties = cosIdProperties;
        this.segmentIdProperties = segmentIdProperties;
        this.distributorFactory = distributorFactory;
        this.idGeneratorProvider = idGeneratorProvider;
        this.prefetchWorkerExecutorService = prefetchWorkerExecutorService;
        this.beanDefinitions = beanDefinitions;
        customizers.orderedStream().forEach(customize -> customize.customize(segmentIdProperties));
    }
    
    /**
     * The bean name of the segment id {@code name}.
     */
    public static String beanName(String name) {
        return name + "SegmentId";
    }
    
    /**
     * The segment ids of the properties: the share, when enabled, and the named ones.
     */
    public Map<String, SegmentIdProperties.IdDefinition> definitions() {
        Map<String, SegmentIdProperties.IdDefinition> definitions = new LinkedHashMap<>();
        if (segmentIdProperties.getShare().isEnabled()) {
            definitions.put(IdGeneratorProvider.SHARE, segmentIdProperties.getShare());
        }
        definitions.putAll(segmentIdProperties.getProvider());
        return definitions;
    }
    
    /**
     * Wrap the created ids in a {@link CountedSegmentId} ({@code cosid.metrics.generated.enabled}).
     *
     * @param countGenerated whether to count the generated ids
     * @since 4.1.0
     */
    public void setCountGenerated(boolean countGenerated) {
        this.countGenerated = countGenerated;
    }
    
    /**
     * The factory method of the {@code {name}SegmentId} beans.
     */
    public SegmentId createBean(String name) {
        SegmentIdProperties.IdDefinition idDefinition = definitions().get(name);
        if (idDefinition == null) {
            throw new IllegalStateException("Segment id [" + name + "] is not configured any more: a customizer removed it.");
        }
        return create(name, idDefinition);
    }
    
    /**
     * Segment ids a customizer added have no bean definition: they are only registered in the provider, here.
     */
    @Override
    public void afterPropertiesSet() {
        definitions().forEach((name, idDefinition) -> {
            if (beanDefinitions != null && beanDefinitions.containsBeanDefinition(beanName(name))) {
                return;
            }
            create(name, idDefinition);
            providerOnly.add(name);
            if (log.isInfoEnabled()) {
                log.info("Segment id [{}] was added by a customizer: registered in the IdGeneratorProvider only, not as a bean.", name);
            }
        });
    }
    
    /**
     * Remove the ids from the provider, where it still holds them, so a later context does not get closed ones; close
     * the ids that are not beans.
     */
    @Override
    public void destroy() throws Exception {
        for (Map.Entry<String, SegmentId> entry : created.entrySet()) {
            if (idGeneratorProvider.get(entry.getKey()).orElse(null) == entry.getValue()) {
                idGeneratorProvider.remove(entry.getKey());
            }
            if (providerOnly.contains(entry.getKey()) && entry.getValue() instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    /**
     * Create the segment id {@code name} and register it in the {@link IdGeneratorProvider} unless one is there.
     */
    public SegmentId create(String name, SegmentIdProperties.IdDefinition idDefinition) {
        IdSegmentDistributorDefinition distributorDefinition = asDistributorDefinition(name, idDefinition);
        IdSegmentDistributor idSegmentDistributor;
        SegmentIdProperties.IdDefinition.Group group = idDefinition.getGroup();
        GroupBySupplier groupBySupplier;
        switch (group.getBy()) {
            case YEAR -> groupBySupplier = new YearGroupBySupplier(group.getPattern());
            case YEAR_MONTH -> groupBySupplier = new YearMonthGroupBySupplier(group.getPattern());
            case YEAR_MONTH_DAY -> groupBySupplier = new YearMonthDayGroupBySupplier(group.getPattern());
            default ->  groupBySupplier = null;
        }

        IdConverterDefinition.GroupPrefix groupPrefix = idDefinition.getConverter() == null ? null : idDefinition.getConverter().getGroupPrefix();
        if (groupBySupplier == null && groupPrefix != null && groupPrefix.isEnabled()) {
            throw new IllegalStateException("Segment id [" + name + "] has converter.group-prefix enabled but no group: set group.by "
                + "(year, year_month or year_month_day), or turn the group prefix off.");
        }
        if (groupBySupplier != null) {
            idSegmentDistributor = new GroupedIdSegmentDistributorFactory(groupBySupplier, distributorFactory).create(distributorDefinition);
        } else {
            idSegmentDistributor = distributorFactory.create(distributorDefinition);
        }

        SegmentId idGenerator = createSegment(segmentIdProperties, idDefinition, idSegmentDistributor, prefetchWorkerExecutorService, countGenerated);
        if (idGeneratorProvider.get(name).isEmpty()) {
            idGeneratorProvider.set(name, idGenerator);
        }
        created.put(name, idGenerator);
        return idGenerator;
    }

    private IdSegmentDistributorDefinition asDistributorDefinition(String name, SegmentIdProperties.IdDefinition idDefinition) {
        String namespace = Namespaces.firstNotBlank(idDefinition.getNamespace(), cosIdProperties.getNamespace());
        return new IdSegmentDistributorDefinition(namespace, name, idDefinition.getOffset(), idDefinition.getStep());
    }

    private static SegmentId createSegment(SegmentIdProperties segmentIdProperties, SegmentIdProperties.IdDefinition idDefinition, IdSegmentDistributor idSegmentDistributor,
                                           PrefetchWorkerExecutorService prefetchWorkerExecutorService, boolean countGenerated) {
        long ttl = MoreObjects.firstNonNull(idDefinition.getTtl(), segmentIdProperties.getTtl());
        SegmentIdProperties.Mode mode = MoreObjects.firstNonNull(idDefinition.getMode(), segmentIdProperties.getMode());

        SegmentId segmentId;
        if (SegmentIdProperties.Mode.SEGMENT.equals(mode)) {
            segmentId = new DefaultSegmentId(ttl, idSegmentDistributor);
        } else {
            SegmentIdProperties.Chain chain = MoreObjects.firstNonNull(idDefinition.getChain(), segmentIdProperties.getChain());
            segmentId = new SegmentChainId(ttl, chain.getSafeDistance(), Math.max(chain.getSafeDistance(), chain.getMaxPrefetchDistance()),
                idSegmentDistributor, prefetchWorkerExecutorService, CallTimeout.of(chain.getPrefetchTimeout()));
        }

        if (countGenerated) {
            segmentId = new CountedSegmentId(segmentId);
        }
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        return new SegmentIdConverterDecorator(segmentId, converterDefinition).decorate();
    }

}
