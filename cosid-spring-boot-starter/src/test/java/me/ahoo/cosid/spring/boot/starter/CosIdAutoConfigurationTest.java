package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.accessor.parser.CompositeFieldDefinitionParser;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void createsProviderPerContextInsteadOfGlobalInstance() {
        AtomicReference<IdGeneratorProvider> first = new AtomicReference<>();
        this.contextRunner.run(context -> {
            IdGeneratorProvider provider = context.getBean(IdGeneratorProvider.class);
            assertThat(provider).isNotSameAs(DefaultIdGeneratorProvider.INSTANCE);
            provider.setShare(MockIdGenerator.INSTANCE);
            first.set(provider);
        });
        this.contextRunner.run(context -> {
            IdGeneratorProvider provider = context.getBean(IdGeneratorProvider.class);
            assertThat(provider).isNotSameAs(first.get());
            assertThat(provider.getShare()).isNull();
        });
        assertThat(DefaultIdGeneratorProvider.INSTANCE.getShare()).isNull();
    }

    @Test
    void clearsProviderWhenContextCloses() {
        AtomicReference<IdGeneratorProvider> holder = new AtomicReference<>();
        this.contextRunner.run(context -> {
            IdGeneratorProvider provider = context.getBean(IdGeneratorProvider.class);
            provider.setShare(MockIdGenerator.INSTANCE);
            provider.set("order", MockIdGenerator.INSTANCE);
            holder.set(provider);
        });
        assertThat(holder.get().getShare()).isNull();
        assertThat(holder.get().getAll()).isEmpty();
    }

    @Test
    void accessorsResolveGeneratorsFromUserProvidedProvider() {
        IdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(MockIdGenerator.usePrefix("user_"));

        this.contextRunner
            .withBean(IdGeneratorProvider.class, () -> provider)
            .run(context -> {
                StringIdEntity entity = new StringIdEntity();
                assertThat(context.getBean(CosIdAccessorRegistry.class).ensureId(entity)).isTrue();
                assertThat(entity.getId()).startsWith("user_");
            });
    }

    public static class StringIdEntity {
        @CosId
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
