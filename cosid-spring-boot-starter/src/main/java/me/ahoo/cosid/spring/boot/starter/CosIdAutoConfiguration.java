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

import me.ahoo.cosid.accessor.parser.CompositeFieldDefinitionParser;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Main auto-configuration class for CosId, providing core beans for ID generation and accessor management.
 *
 * <p>This configuration class sets up the fundamental components of the CosId framework:
 * <ul>
 *   <li>ID generator provider for managing different types of ID generators</li>
 *   <li>Field definition parsers for identifying ID fields in entities</li>
 *   <li>Accessor parser and registry for runtime ID field access</li>
 * </ul>
 *
 * <p>The configuration is enabled when {@link ConditionalOnCosIdEnabled} conditions are met
 * and automatically registers the necessary Spring beans with appropriate ordering and priorities.</p>
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@EnableConfigurationProperties(CosIdProperties.class)
public class CosIdAutoConfiguration {

    /**
     * Provides the default ID generator provider instance.
     *
     * <p>This bean supplies a singleton instance of {@link DefaultIdGeneratorProvider}
     * that manages all registered ID generators in the application. It serves as the
     * central registry for accessing ID generators by name.</p>
     *
     * @return the default ID generator provider instance
     */
    @Bean
    @ConditionalOnMissingBean
    public IdGeneratorProvider idGeneratorProvider() {
        return DefaultIdGeneratorProvider.INSTANCE;
    }

    /**
     * Provides the annotation-based field definition parser with highest precedence.
     *
     * <p>This parser identifies ID fields marked with CosId annotations and has the
     * highest precedence to ensure annotation-based configuration takes priority.</p>
     *
     * @return the annotation field definition parser instance
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Bean
    public FieldDefinitionParser annotationDefinitionParser() {
        return AnnotationDefinitionParser.INSTANCE;
    }

    /**
     * Creates a composite field definition parser that combines multiple parsers.
     *
     * <p>This primary parser aggregates all available {@link FieldDefinitionParser}
     * beans and tries them in order until one successfully identifies ID fields.
     * The composite approach allows for flexible field identification strategies.</p>
     *
     * @param fieldDefinitionParsers list of all available field definition parsers
     * @return a composite field definition parser
     */
    @Primary
    @Bean
    public FieldDefinitionParser compositeFieldDefinitionParser(List<FieldDefinitionParser> fieldDefinitionParsers) {
        return new CompositeFieldDefinitionParser(fieldDefinitionParsers);
    }

    /**
     * Provides the default CosId accessor parser for runtime field access.
     *
     * <p>This parser analyzes classes and creates accessors for ID fields identified
     * by the field definition parser. It enables runtime reading and writing of ID values.</p>
     *
     * @param definitionParser the field definition parser to use for field identification
     * @return a new default accessor parser instance
     */
    @Bean
    @ConditionalOnMissingBean
    public CosIdAccessorParser cosIdAccessorParser(FieldDefinitionParser definitionParser) {
        return new DefaultAccessorParser(definitionParser);
    }

    /**
     * Provides the default CosId accessor registry for managing field accessors.
     *
     * <p>This registry maintains a cache of accessor parsers for different entity classes
     * and provides efficient access to ID field manipulation capabilities.</p>
     *
     * @param cosIdAccessorParser the accessor parser to use for creating accessors
     * @return a new default accessor registry instance
     */
    @Bean
    @ConditionalOnMissingBean
    public CosIdAccessorRegistry cosIdAccessorRegistry(CosIdAccessorParser cosIdAccessorParser) {
        return new DefaultAccessorRegistry(cosIdAccessorParser);
    }
}
