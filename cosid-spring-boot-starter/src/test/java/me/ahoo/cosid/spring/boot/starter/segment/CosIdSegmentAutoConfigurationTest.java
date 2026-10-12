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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdSegmentAutoConfiguration.class))
        .withBean(IdSegmentDistributorFactory.class, () -> definition -> {
            IdSegmentDistributor distributor = mock(IdSegmentDistributor.class);
            org.mockito.Mockito.when(distributor.getNamespace()).thenReturn(definition.getNamespace());
            org.mockito.Mockito.when(distributor.getName()).thenReturn(definition.getName());
            org.mockito.Mockito.when(distributor.getStep()).thenReturn(definition.getStep());
            return distributor;
        });

    @Test
    void registersDefaultAndCustomizedSegmentIdsWithStubDistributorFactory() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withBean(CustomizeSegmentIdProperties.class, () -> idProperties -> idProperties.getProvider().put("test", new SegmentIdProperties.IdDefinition()))
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSegmentAutoConfiguration.class)
                    .hasSingleBean(PrefetchWorkerExecutorService.class)
                    .hasSingleBean(CosIdLifecyclePrefetchWorkerExecutorService.class)
                    .hasBean("__share__SegmentId")
                    .doesNotHaveBean("testSegmentId")
                ;
                assertThat(context.getBean(IdGeneratorProvider.class).get("test")).isPresent();
            });
    }

    @Test
    void registersConfiguredSegmentIdAndDisablesShareId() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".share.enabled=false")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.group.by=year")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.group.pattern=yyyy")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.type=to_string")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.group-prefix.enabled=true")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.to_string.pad-start=true")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSegmentAutoConfiguration.class)
                    .hasSingleBean(PrefetchWorkerExecutorService.class)
                    .hasSingleBean(CosIdLifecyclePrefetchWorkerExecutorService.class)
                    .doesNotHaveBean("__share__SegmentId")
                    .hasBean("testSegmentId")
                ;
            });
    }

    @Test
    void registersConfiguredSegmentIdGroupedByYearMonth() {
        this.contextRunner
                .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".share.enabled=false")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.group.by=year_month")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.group.pattern=yyyyMM")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.type=to_string")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.group-prefix.enabled=true")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.to_string.pad-start=true")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(CosIdSegmentAutoConfiguration.class)
                            .hasSingleBean(PrefetchWorkerExecutorService.class)
                            .hasSingleBean(CosIdLifecyclePrefetchWorkerExecutorService.class)
                            .doesNotHaveBean("__share__SegmentId")
                            .hasBean("testSegmentId")
                    ;
                });
    }

    @Test
    void registersConfiguredSegmentIdGroupedByYearMonthDay() {
        this.contextRunner
                .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".share.enabled=false")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.group.by=year_month_day")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.group.pattern=yyyyMMdd")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.type=to_string")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.group-prefix.enabled=true")
                .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.to_string.pad-start=true")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(CosIdSegmentAutoConfiguration.class)
                            .hasSingleBean(PrefetchWorkerExecutorService.class)
                            .hasSingleBean(CosIdLifecyclePrefetchWorkerExecutorService.class)
                            .doesNotHaveBean("__share__SegmentId")
                            .hasBean("testSegmentId")
                    ;
                });
    }

    @Test
    void doesNotCreateSegmentBeansWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSegmentAutoConfiguration.class)
                .doesNotHaveBean(PrefetchWorkerExecutorService.class)
                .doesNotHaveBean(CosIdLifecyclePrefetchWorkerExecutorService.class));
    }

    @Test
    void groupPrefixWithoutGroupFailsAtStartup() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".share.enabled=false")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.type=to_string")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.group-prefix.enabled=true")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("Segment id [test] has converter.group-prefix enabled but no group"));
    }
}
