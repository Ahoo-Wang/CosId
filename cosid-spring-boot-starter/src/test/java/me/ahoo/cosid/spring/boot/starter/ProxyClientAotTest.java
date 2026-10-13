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

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdProxyMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdProxySegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.javapoet.ClassName;

/**
 * Spring AOT processing of an application on the proxy, with {@code cosid.proxy.client=http-interface}.
 */
class ProxyClientAotTest {
    
    @Configuration(proxyBeanMethods = false)
    static class LocalState {
        @Bean
        MachineStateStorage machineStateStorage() {
            return MachineStateStorage.IN_MEMORY;
        }
        
        @Bean
        ClockBackwardsSynchronizer clockBackwardsSynchronizer() {
            return ClockBackwardsSynchronizer.DEFAULT;
        }
    }
    
    @Test
    void aheadOfTimeProcessingSucceedsWithHttpInterfaceClients() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true",
                MachineProperties.Distributor.TYPE + "=proxy",
                ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true",
                SegmentIdProperties.Distributor.TYPE + "=proxy",
                ProxyClientCondition.CLIENT_KEY + "=http-interface",
                "cosid.proxy.host=http://cosid-proxy:8688").applyTo(context);
            context.register(CosIdAutoConfiguration.class, CosIdProxyMachineIdDistributorAutoConfiguration.class,
                CosIdProxySegmentAutoConfiguration.class, LocalState.class);
            InMemoryGeneratedFiles files = new InMemoryGeneratedFiles();
            DefaultGenerationContext generationContext = new DefaultGenerationContext(new ClassNameGenerator(ClassName.get(getClass())), files);
            
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
            generationContext.writeGeneratedContent();
            
            assertThat(files.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)).isNotEmpty();
            assertThat(context.getBeanFactory().containsBeanDefinition("cosIdProxyMachineLeaseClient")).isTrue();
            assertThat(context.getBeanFactory().containsBeanDefinition("cosIdProxySegmentClient")).isTrue();
        }
    }
}
