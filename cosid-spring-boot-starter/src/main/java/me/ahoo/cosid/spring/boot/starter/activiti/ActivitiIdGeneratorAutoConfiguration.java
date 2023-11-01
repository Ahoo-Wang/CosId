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

package me.ahoo.cosid.spring.boot.starter.activiti;

import me.ahoo.cosid.activiti.ActivitiIdGenerator;
import me.ahoo.cosid.flowable.FlowableIdGenerator;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

import org.activiti.spring.SpringProcessEngineConfiguration;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Activiti IdGenerator Auto Configuration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass(FlowableIdGenerator.class)
public class ActivitiIdGeneratorAutoConfiguration {
    
    @Bean
    public ProcessEngineConfigurationConfigurer engineConfigurationConfigurer() {
        return new ActivitiIdGeneratorAutoConfiguration.CosIdProcessEngineConfigurationConfigurer();
    }
    
    static class CosIdProcessEngineConfigurationConfigurer implements ProcessEngineConfigurationConfigurer {
        
        @Override
        public void configure(SpringProcessEngineConfiguration engineConfiguration) {
            engineConfiguration.setIdGenerator(new ActivitiIdGenerator());
        }
    }
}
