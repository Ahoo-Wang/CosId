package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@EnableConfigurationProperties(CosIdProperties.class)
public class CosIdAutoConfiguration {
    private final CosIdProperties cosIdProperties;

    public CosIdAutoConfiguration(CosIdProperties cosIdProperties) {
        this.cosIdProperties = cosIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public IdGeneratorProvider idGeneratorProvider() {
        return new DefaultIdGeneratorProvider();
    }
}
