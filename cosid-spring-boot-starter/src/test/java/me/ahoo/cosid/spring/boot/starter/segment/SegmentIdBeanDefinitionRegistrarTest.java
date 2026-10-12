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

package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.concurrent.atomic.AtomicReference;

class SegmentIdBeanDefinitionRegistrarTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdSegmentAutoConfiguration.class))
        .withBean(IdSegmentDistributorFactory.class, () -> definition -> new AtomicIdSegmentDistributor())
        .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", "cosid.provider.isolated=true",
            SegmentIdProperties.PREFIX + ".share.enabled=false", SegmentIdProperties.PREFIX + ".provider.order.step=10");
    
    @Test
    void segmentIdsAreBeanDefinitionsThatOtherBeansCanDependOn() {
        contextRunner.withUserConfiguration(DependsOnOrder.class).run(context -> {
            assertThat(context.getBeanFactory().containsBeanDefinition("orderSegmentId")).isTrue();
            SegmentId order = context.getBean("orderSegmentId", SegmentId.class);
            assertThat(context.getBean(IdGeneratorProvider.class).getRequired("order")).isSameAs(order);
            assertThat(context.getBean(DependsOnOrder.class).seen.get()).isSameAs(order);
        });
    }
    
    @Test
    void closingTheContextClosesTheSegmentChain() {
        AtomicReference<SegmentChainId> chain = new AtomicReference<>();
        contextRunner.run(context -> {
            SegmentId order = context.getBean("orderSegmentId", SegmentId.class);
            chain.set((SegmentChainId) Decorator.getActual(order));
            assertThat(chain.get().isClosed()).isFalse();
        });
        assertThat(chain.get().isClosed()).isTrue();
    }
    
    @Test
    void customizerAddsSegmentIdsAndIsThePropertiesBean() {
        contextRunner
            .withBean(CustomizeSegmentIdProperties.class, () -> properties -> properties.getProvider().put("added", new SegmentIdProperties.IdDefinition()))
            .run(context -> {
                assertThat(context).hasBean("addedSegmentId");
                assertThat(context.getBean(SegmentIdProperties.class).getProvider()).containsKeys("order", "added");
            });
    }
    
    @Configuration(proxyBeanMethods = false)
    static class DependsOnOrder {
        final AtomicReference<Object> seen = new AtomicReference<>();
        
        @Bean
        @DependsOn("orderSegmentId")
        Object readsTheProvider(IdGeneratorProvider idGeneratorProvider) {
            seen.set(idGeneratorProvider.getRequired("order"));
            return new Object();
        }
    }
}
