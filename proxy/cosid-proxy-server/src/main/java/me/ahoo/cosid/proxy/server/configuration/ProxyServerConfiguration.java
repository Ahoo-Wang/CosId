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

package me.ahoo.cosid.proxy.server.configuration;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.config.BlockingExecutionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Binds {@link ProxyServerProperties} and runs the API calls, which block on the stores, off the event loop.
 *
 * <p>With platform threads, on a bounded pool ({@code cosid.proxy.server.blocking.*}). With virtual threads
 * ({@code spring.threads.virtual.enabled=true} on Java 21+), Spring Boot already runs them on its virtual-thread
 * executor, and this pool is not created.</p>
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProxyServerProperties.class)
public class ProxyServerConfiguration {
    
    /**
     * The bounded pool for the API calls on platform threads. Not an {@code Executor} bean, so that it never replaces
     * the application's task executor.
     *
     * @param proxyServerProperties the server properties
     * @return the blocking execution
     */
    @Bean
    @ConditionalOnThreading(Threading.PLATFORM)
    public ProxyBlockingExecution cosIdProxyBlockingExecution(ProxyServerProperties proxyServerProperties) {
        return new ProxyBlockingExecution(proxyServerProperties.getBlocking());
    }
    
    /**
     * Runs controller methods with a non-reactive return type, that is every CosId API call, on its own pool.
     */
    public static class ProxyBlockingExecution implements WebFluxConfigurer, InitializingBean, DisposableBean {
        private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        public ProxyBlockingExecution(ProxyServerProperties.Blocking blocking) {
            executor.setThreadNamePrefix("cosid-proxy-blocking-");
            executor.setCorePoolSize(blocking.getThreads());
            executor.setMaxPoolSize(blocking.getThreads());
            executor.setQueueCapacity(blocking.getQueueCapacity());
        }
        
        @Override
        public void afterPropertiesSet() {
            executor.initialize();
        }
        
        @Override
        public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
            configurer.setExecutor(executor);
        }
        
        /**
         * API calls running now.
         *
         * @return the number of busy threads
         */
        public int getActiveCount() {
            return executor.getActiveCount();
        }
        
        @Override
        public void destroy() {
            executor.shutdown();
        }
    }
}
