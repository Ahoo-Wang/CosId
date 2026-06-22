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

package me.ahoo.cosid.spring.boot.starter.actuate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

class CosIdStringGeneratorEndpointTest {

    @Test
    void shareGenerateAsStringUsesShareGenerator() {
        CosIdStringGeneratorEndpoint endpoint = new CosIdStringGeneratorEndpoint(newProvider());

        assertThat(endpoint.shareGenerateAsString()).isNotBlank();
    }

    @Test
    void generateAsStringUsesNamedGenerator() {
        CosIdStringGeneratorEndpoint endpoint = new CosIdStringGeneratorEndpoint(newProvider());

        assertThat(endpoint.generateAsString("orders")).isNotBlank();
    }

    @Test
    void generateAsStringPropagatesMissingGeneratorFailure() {
        CosIdStringGeneratorEndpoint endpoint = new CosIdStringGeneratorEndpoint(new DefaultIdGeneratorProvider());

        assertThatThrownBy(() -> endpoint.generateAsString("missing"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("missing");
    }

    private static DefaultIdGeneratorProvider newProvider() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.setShare(MockIdGenerator.INSTANCE);
        provider.set("orders", MockIdGenerator.INSTANCE);
        return provider;
    }
}
