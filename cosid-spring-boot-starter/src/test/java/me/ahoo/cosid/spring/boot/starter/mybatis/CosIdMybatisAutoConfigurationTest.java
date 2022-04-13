package me.ahoo.cosid.spring.boot.starter.mybatis;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.mybatis.CosIdPlugin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdMybatisAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdMybatisAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdMybatisAutoConfiguration.class));
    
    //TODO
    @Disabled
    @Test
    void contextLoads() {
        this.contextRunner
            .withUserConfiguration(CosIdMybatisAutoConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(CosIdMybatisAutoConfiguration.class);
                assertThat(context).hasSingleBean(CosIdPlugin.class);
            });
    }
}
