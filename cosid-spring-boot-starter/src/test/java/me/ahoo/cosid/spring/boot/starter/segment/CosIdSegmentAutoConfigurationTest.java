package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import me.ahoo.cosid.mybatis.CosIdPlugin;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.mybatis.CosIdMybatisAutoConfiguration;

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
            .withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/cosid_db_0")
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
}
