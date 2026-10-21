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

package me.ahoo.cosid.spring.boot.starter.axon;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.axon.CosIdIdentifierFactory;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Binds the application context's {@link IdGeneratorProvider} to Axon's {@link CosIdIdentifierFactory}.
 *
 * <p>Axon loads its {@code IdentifierFactory} through {@link java.util.ServiceLoader}, so the provider is bound
 * statically while this context is alive and unbound when it closes.</p>
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnClass(CosIdIdentifierFactory.class)
public class CosIdAxonAutoConfiguration {
    /**
     * The id generator of Axon's identifiers. Unset, the {@code CosIdIdentifierFactory#ID_KEY} system property's, else
     * the shared one.
     *
     * @since 4.1.0
     */
    public static final String GENERATOR_NAME_KEY = CosId.COSID_PREFIX + "axon.generator-name";
    
    private final Environment environment;
    
    public CosIdAxonAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CosIdAxonProviderBinding cosIdAxonProviderBinding(IdGeneratorProvider idGeneratorProvider) {
        String generatorName = environment.getProperty(GENERATOR_NAME_KEY);
        return new CosIdAxonProviderBinding(idGeneratorProvider, generatorName == null || generatorName.isBlank() ? null : generatorName);
    }

    /**
     * Keeps {@link CosIdIdentifierFactory} bound to a provider for the lifetime of the bean.
     */
    public static class CosIdAxonProviderBinding implements DisposableBean {
        private final IdGeneratorProvider idGeneratorProvider;

        public CosIdAxonProviderBinding(IdGeneratorProvider idGeneratorProvider) {
            this(idGeneratorProvider, null);
        }
        
        /**
         * Binds the provider, and the generator that Axon's factory uses.
         *
         * @param generatorName {@code null} for the system property's
         * @since 4.1.0
         */
        public CosIdAxonProviderBinding(IdGeneratorProvider idGeneratorProvider, @Nullable String generatorName) {
            this.idGeneratorProvider = idGeneratorProvider;
            CosIdIdentifierFactory.bind(idGeneratorProvider, generatorName);
        }

        @Override
        public void destroy() {
            CosIdIdentifierFactory.unbind(idGeneratorProvider);
        }
    }
}
