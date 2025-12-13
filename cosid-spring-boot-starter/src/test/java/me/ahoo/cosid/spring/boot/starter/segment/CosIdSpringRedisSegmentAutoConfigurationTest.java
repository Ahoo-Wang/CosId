package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdSpringRedisSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSpringRedisSegmentAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withUserConfiguration(DataRedisAutoConfiguration.class, CosIdSpringRedisSegmentAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                    .hasSingleBean(SegmentIdProperties.class)
                    .hasSingleBean(IdSegmentDistributorFactory.class)
                ;
            });
    }
    
    @Test
    void contextLoadsWhenMissSpringRedisIdSegmentDistributor() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withUserConfiguration(DataRedisAutoConfiguration.class, CosIdSpringRedisSegmentAutoConfiguration.class)
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
