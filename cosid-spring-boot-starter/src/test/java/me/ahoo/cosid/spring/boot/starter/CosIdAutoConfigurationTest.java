package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.accessor.parser.CompositeFieldDefinitionParser;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdAutoConfiguration Test .
 *
 * @author ahoo wang
 */
public class CosIdAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void contextLoads() {
        this.contextRunner
            .withUserConfiguration(CosIdAutoConfiguration.class)
            .run(context -> assertThat(context)
                .hasSingleBean(CosIdAutoConfiguration.class)
                .hasSingleBean(CosIdProperties.class)
                .hasSingleBean(IdGeneratorProvider.class)
                .hasSingleBean(AnnotationDefinitionParser.class)
                .hasSingleBean(CompositeFieldDefinitionParser.class)
                .hasSingleBean(CosIdAccessorParser.class));
    }

    @Test
    void contextLoadsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .withUserConfiguration(CosIdAutoConfiguration.class)
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdAutoConfiguration.class));
    }
}
