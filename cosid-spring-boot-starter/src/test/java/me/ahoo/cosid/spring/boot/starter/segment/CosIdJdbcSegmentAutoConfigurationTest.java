package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdJdbcSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdJdbcSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/cosid_db_0")
            .withUserConfiguration(DataSourceAutoConfiguration.class, CosIdJdbcSegmentAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdJdbcSegmentAutoConfiguration.class)
                    .hasSingleBean(SegmentIdProperties.class)
                    .hasSingleBean(JdbcIdSegmentInitializer.class)
                    .hasSingleBean(IdSegmentDistributorFactory.class)
                ;
            });
    }
}
