package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CosIdAutoConfiguration Test .
 *
 * @author ahoo wang
 */

public class CosIdAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class));
    
    @Test
    void contextLoads() {
        this.contextRunner
            .withUserConfiguration(CosIdAutoConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(CosIdAutoConfiguration.class);
                assertThat(context).hasSingleBean(IdGeneratorProvider.class);
                assertThat(context).hasSingleBean(FieldDefinitionParser.class);
            });
    }
}
