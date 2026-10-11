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

package me.ahoo.cosid.spring.boot.starter.jdbc;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.EnabledSuffix;
import me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback;
import me.ahoo.cosid.spring.data.jdbc.IdAnnotationDefinitionParser;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

/**
 * CosId Mybatis AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass(CosIdBeforeConvertCallback.class)
@ConditionalOnProperty(value = CosIdJdbcAutoConfiguration.ENABLED_KEY, havingValue = "true")
public class CosIdJdbcAutoConfiguration {
    /**
     * Turns the Spring Data JDBC integration ({@code @Id} fields and the before-convert callback) on with {@code true}.
     * Default {@code false} since 4.0, because the {@code @Id} parser joins the global field definition parser and also
     * takes over {@code @Id} fields on other paths, such as MyBatis.
     */
    public static final String ENABLED_KEY = CosId.COSID_PREFIX + "jdbc" + EnabledSuffix.KEY;

    @Bean
    @ConditionalOnMissingBean(IdAnnotationDefinitionParser.class)
    public FieldDefinitionParser idAnnotationDefinitionParser() {
        return IdAnnotationDefinitionParser.INSTANCE;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdBeforeConvertCallback cosIdBeforeConvertCallback(CosIdAccessorRegistry accessorRegistry) {
        return new CosIdBeforeConvertCallback(accessorRegistry);
    }

    /**
     * Registers the accessors of every Spring Data JDBC entity once every singleton, and so every ID generator, is
     * created, so a {@code long} or {@code int} id field bound to a generator of string ids only fails startup
     * instead of the first insert.
     *
     * @param mappingContexts the Spring Data relational mapping contexts
     * @param accessorRegistry the accessor registry
     * @return the registrar
     */
    @Bean
    public SmartInitializingSingleton cosIdJdbcEntityAccessorRegistrar(ObjectProvider<RelationalMappingContext> mappingContexts,
                                                                       CosIdAccessorRegistry accessorRegistry) {
        return () -> mappingContexts.orderedStream()
            .flatMap(mappingContext -> mappingContext.getPersistentEntities().stream())
            .forEach(persistentEntity -> accessorRegistry.get(persistentEntity.getType()));
    }
    
}
