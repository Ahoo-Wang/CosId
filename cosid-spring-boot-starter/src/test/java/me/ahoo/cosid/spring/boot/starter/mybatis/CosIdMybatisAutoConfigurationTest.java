package me.ahoo.cosid.spring.boot.starter.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.mybatis.CosIdPlugin;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdMybatisAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdMybatisAutoConfiguration.class))
        .withBean(CosIdAccessorRegistry.class, () -> mock(CosIdAccessorRegistry.class));

    @Test
    void createsCosIdPluginWhenMybatisAdapterIsPresent() {
        this.contextRunner.run(context -> assertThat(context).hasSingleBean(CosIdPlugin.class));
    }

    @Test
    void backsOffWhenUserProvidesCosIdPlugin() {
        CosIdPlugin plugin = mock(CosIdPlugin.class);

        this.contextRunner
            .withBean(CosIdPlugin.class, () -> plugin)
            .run(context -> assertThat(context.getBean(CosIdPlugin.class)).isSameAs(plugin));
    }

    @Test
    void doesNotCreatePluginWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(CosIdPlugin.class));
    }

    @Test
    void doesNotCreatePluginWhenMybatisAdapterClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(CosIdPlugin.class))
            .run(context -> assertThat(context).doesNotHaveBean(CosIdPlugin.class));
    }
}
