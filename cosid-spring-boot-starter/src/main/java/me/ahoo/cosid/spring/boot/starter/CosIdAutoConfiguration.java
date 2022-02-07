/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CosId Auto Configuration.
 *
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
        return DefaultIdGeneratorProvider.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldDefinitionParser fieldDefinitionParser() {
        return AnnotationDefinitionParser.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public CosIdAccessorParser cosIdAccessorParser(FieldDefinitionParser definitionParser) {
        return new DefaultAccessorParser(definitionParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public CosIdAccessorRegistry cosIdAccessorRegistry(CosIdAccessorParser cosIdAccessorParser) {
        return new DefaultAccessorRegistry(cosIdAccessorParser);
    }
}
