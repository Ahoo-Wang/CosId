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

import me.ahoo.cosid.accessor.parser.FieldDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback;
import me.ahoo.cosid.spring.data.jdbc.IdAnnotationDefinitionParser;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * CosId Mybatis AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass(CosIdBeforeConvertCallback.class)
public class CosIdJdbcAutoConfiguration {
    @Bean
    public FieldDefinitionParser idAnnotationDefinitionParser() {
        return IdAnnotationDefinitionParser.INSTANCE;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdBeforeConvertCallback cosIdBeforeConvertCallback(CosIdAccessorRegistry accessorRegistry) {
        return new CosIdBeforeConvertCallback(accessorRegistry);
    }
    
}
