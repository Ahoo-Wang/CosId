package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * CosIdSpringRedisSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSpringRedisSegmentAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdSpringRedisSegmentAutoConfiguration.class))
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
    
    @Test
    void createsRedisSegmentFactoryWithoutConnectingToRedis() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                    .hasSingleBean(SegmentIdProperties.class)
                    .hasSingleBean(IdSegmentDistributorFactory.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesDistributorFactory() {
        IdSegmentDistributorFactory factory = definition -> mock(me.ahoo.cosid.segment.IdSegmentDistributor.class);

        this.contextRunner
            .withBean(IdSegmentDistributorFactory.class, () -> factory)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .hasSingleBean(IdSegmentDistributorFactory.class)
                .getBean(IdSegmentDistributorFactory.class)
                .isSameAs(factory));
    }

    @Test
    void doesNotCreateRedisSegmentFactoryWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                .doesNotHaveBean(SegmentIdProperties.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateRedisSegmentFactoryWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                .doesNotHaveBean(SegmentIdProperties.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }
    
    @Test
    void doesNotCreateRedisSegmentFactoryWhenRedisDistributorClassIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .withClassLoader(new FilteredClassLoader(SpringRedisIdSegmentDistributor.class))
            .run(context -> {
                assertThat(context)
                    .doesNotHaveBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                    .doesNotHaveBean(SegmentIdProperties.class)
                    .doesNotHaveBean(IdSegmentDistributorFactory.class)
                ;
            });
    }
}
