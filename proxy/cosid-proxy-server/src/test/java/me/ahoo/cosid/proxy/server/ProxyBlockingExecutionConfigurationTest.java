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

package me.ahoo.cosid.proxy.server;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.proxy.server.configuration.ProxyServerConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;

/**
 * The API pool never replaces the application's task executor.
 */
class ProxyBlockingExecutionConfigurationTest {
    
    @Test
    void thePoolIsNotAnExecutorBean() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(ProxyServerConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(ProxyServerConfiguration.ProxyBlockingExecution.class);
                assertThat(context).hasBean("applicationTaskExecutor");
                assertThat(context.getBeansOfType(TaskExecutor.class)).containsOnlyKeys("applicationTaskExecutor");
            });
    }
}
