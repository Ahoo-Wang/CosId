package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.accessor.parser.CompositeFieldDefinitionParser;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class));

    @Test
    void createsCoreBeansWhenCosIdIsEnabledByDefault() {
        this.contextRunner.run(context -> {
            AssertionsForInterfaceTypes.assertThat(context)
                .hasSingleBean(CosIdProperties.class)
                .hasSingleBean(IdGeneratorProvider.class)
                .hasSingleBean(AnnotationDefinitionParser.class)
                .hasSingleBean(CompositeFieldDefinitionParser.class)
                .hasSingleBean(CosIdAccessorParser.class)
                .hasSingleBean(CosIdAccessorRegistry.class);

            assertThat(context.getBean(FieldDefinitionParser.class))
                .isInstanceOf(CompositeFieldDefinitionParser.class);
        });
    }

    @Test
    void backsOffForUserProvidedProviderAndAccessorInfrastructure() {
        IdGeneratorProvider provider = mock(IdGeneratorProvider.class);
        CosIdAccessorParser parser = mock(CosIdAccessorParser.class);
        CosIdAccessorRegistry registry = mock(CosIdAccessorRegistry.class);

        this.contextRunner
            .withBean(IdGeneratorProvider.class, () -> provider)
            .withBean(CosIdAccessorParser.class, () -> parser)
            .withBean(CosIdAccessorRegistry.class, () -> registry)
            .run(context -> {
                AssertionsForInterfaceTypes.assertThat(context)
                    .hasSingleBean(IdGeneratorProvider.class)
                    .hasSingleBean(CosIdAccessorParser.class)
                    .hasSingleBean(CosIdAccessorRegistry.class);
                assertThat(context.getBean(IdGeneratorProvider.class)).isSameAs(provider);
                assertThat(context.getBean(CosIdAccessorParser.class)).isSameAs(parser);
                assertThat(context.getBean(CosIdAccessorRegistry.class)).isSameAs(registry);
            });
    }

    @Test
    void bindsCosIdPropertiesIntoAutoConfiguredContext() {
        this.contextRunner
            .withPropertyValues(
                "cosid.namespace=orders",
                "cosid.proxy.host=http://cosid-proxy:8688"
            )
            .run(context -> assertThat(context.getBean(CosIdProperties.class))
                .extracting(CosIdProperties::getNamespace, properties -> properties.getProxy().getHost())
                .containsExactly("orders", "http://cosid-proxy:8688"));
    }

    @Test
    void doesNotCreateCoreBeansWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> AssertionsForInterfaceTypes.assertThat(context)
                .doesNotHaveBean(CosIdProperties.class)
                .doesNotHaveBean(IdGeneratorProvider.class)
                .doesNotHaveBean(CosIdAccessorParser.class)
                .doesNotHaveBean(CosIdAccessorRegistry.class));
    }
}
