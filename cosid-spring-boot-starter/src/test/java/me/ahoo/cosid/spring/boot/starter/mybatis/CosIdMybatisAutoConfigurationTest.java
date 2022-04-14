package me.ahoo.cosid.spring.boot.starter.mybatis;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.mybatis.CosIdPlugin;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdMybatisAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdMybatisAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withUserConfiguration(CosIdAutoConfiguration.class, CosIdMybatisAutoConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdMybatisAutoConfiguration.class)
                    .hasSingleBean(CosIdPlugin.class);
            });
    }
}
