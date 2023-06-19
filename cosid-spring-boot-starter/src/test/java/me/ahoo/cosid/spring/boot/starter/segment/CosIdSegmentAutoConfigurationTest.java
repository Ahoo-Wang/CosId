package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/cosid_db")
            .withPropertyValues("spring.datasource.username=root")
            .withPropertyValues("spring.datasource.password=root")
            .withUserConfiguration(CosIdAutoConfiguration.class, DataSourceAutoConfiguration.class, CosIdJdbcSegmentAutoConfiguration.class, CosIdSegmentAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSegmentAutoConfiguration.class)
                    .hasSingleBean(PrefetchWorkerExecutorService.class)
                    .hasSingleBean(CosIdLifecyclePrefetchWorkerExecutorService.class)
                    .hasSingleBean(SegmentId.class)
                ;
            });
    }
    
    @Test
    void contextLoadsWithConfig() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/cosid_db")
            .withPropertyValues("spring.datasource.username=root")
            .withPropertyValues("spring.datasource.password=root")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".share.enabled=false")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.type=to_string")
            .withPropertyValues(SegmentIdProperties.PREFIX + ".provider.test.converter.to_string.pad-start=true")
            .withUserConfiguration(CosIdAutoConfiguration.class, DataSourceAutoConfiguration.class, CosIdJdbcSegmentAutoConfiguration.class, CosIdSegmentAutoConfiguration.class)
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
}
