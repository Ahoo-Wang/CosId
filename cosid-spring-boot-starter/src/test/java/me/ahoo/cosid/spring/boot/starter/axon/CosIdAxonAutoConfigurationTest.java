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

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.axon.CosIdIdentifierFactory;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicReference;

class CosIdAxonAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdAxonAutoConfiguration.class));

    @Test
    void bindsContextProviderWhileContextIsOpen() {
        AtomicReference<IdGeneratorProvider> holder = new AtomicReference<>();
        this.contextRunner
            .withPropertyValues(CosIdAutoConfiguration.PROVIDER_ISOLATED_KEY + "=true")
            .run(context -> {
                assertThat(context).hasSingleBean(CosIdAxonAutoConfiguration.CosIdAxonProviderBinding.class);
                IdGeneratorProvider provider = context.getBean(IdGeneratorProvider.class);
                provider.setShare(MockIdGenerator.usePrefix("ctx_"));
                holder.set(provider);

                assertThat(new CosIdIdentifierFactory().generateIdentifier()).startsWith("ctx_");
            });
        assertThat(CosIdIdentifierFactory.unbind(holder.get())).isFalse();
    }

    @Test
    void doesNotBindWhenCosIdIsDisabled() {
        this.contextRunner
            .withPropertyValues("cosid.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(CosIdAxonAutoConfiguration.CosIdAxonProviderBinding.class));
    }

    @Test
    void doesNotBindWhenAxonAdapterIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(CosIdIdentifierFactory.class))
            .run(context -> assertThat(context).doesNotHaveBean(CosIdAxonAutoConfiguration.CosIdAxonProviderBinding.class));
    }
}
