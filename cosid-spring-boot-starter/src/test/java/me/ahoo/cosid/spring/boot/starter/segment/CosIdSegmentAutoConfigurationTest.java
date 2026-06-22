package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

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
                    .hasBean("testSegmentId")
                ;
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
}
