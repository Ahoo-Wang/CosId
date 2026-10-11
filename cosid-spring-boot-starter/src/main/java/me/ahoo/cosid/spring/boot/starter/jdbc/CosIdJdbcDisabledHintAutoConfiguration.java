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

import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.data.jdbc.CosIdBeforeConvertCallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * With the Spring Data JDBC integration off (the 4.0 default), says once at startup which Spring Data JDBC entities
 * have an {@code @Id} property or {@link CosId} fields that CosId would fill with the integration on.
 *
 * @author ahoo wang
 * @since 4.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass({CosIdBeforeConvertCallback.class, RelationalMappingContext.class})
@ConditionalOnProperty(value = CosIdJdbcAutoConfiguration.ENABLED_KEY, matchIfMissing = true, havingValue = "false")
public class CosIdJdbcDisabledHintAutoConfiguration {
    
    @Bean
    static SmartInitializingSingleton cosIdJdbcDisabledHint(ObjectProvider<RelationalMappingContext> mappingContexts) {
        return () -> {
            List<String> entities = new ArrayList<>();
            mappingContexts.orderedStream().forEach(mappingContext -> {
                for (RelationalPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
                    // With the integration on, CosId fills the @Id property and @CosId fields of these entities.
                    if (entity.hasIdProperty() || hasCosIdField(entity.getType())) {
                        entities.add(entity.getType().getName());
                    }
                }
            });
            if (!entities.isEmpty() && log.isInfoEnabled()) {
                log.info("Spring Data JDBC entities {} have @Id or @CosId fields that CosId no longer fills, because the CosId "
                    + "Spring Data JDBC integration is off by default since 4.0: set [{}=true] to fill them.", entities,
                    CosIdJdbcAutoConfiguration.ENABLED_KEY);
            }
        };
    }
    
    static boolean hasCosIdField(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(CosId.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
