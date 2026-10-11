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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import me.ahoo.coapi.spring.CoApiDefinition;
import me.ahoo.coapi.spring.client.reactive.WebClientBuilderCustomizer;
import me.ahoo.coapi.spring.client.sync.RestClientBuilderCustomizer;
import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.proxy.api.SegmentClient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

class CosIdProxyClientAuthConfigurationTest {
    
    private static ApplicationContextRunner contextRunner(String token) {
        return new ApplicationContextRunner()
            .withUserConfiguration(CosIdProxyClientAuthConfiguration.class)
            .withBean(CosIdProperties.class, () -> {
                CosIdProperties properties = new CosIdProperties();
                properties.getProxy().setToken(token);
                return properties;
            });
    }
    
    private static CoApiDefinition definition(Class<?> apiType) {
        return new CoApiDefinition(apiType.getSimpleName(), apiType, "http://localhost", false);
    }
    
    @Test
    void sendsBearerTokenFromProxyClients() {
        contextRunner("secret").run(context -> {
            RestClient.Builder restBuilder = mock(RestClient.Builder.class);
            context.getBean(RestClientBuilderCustomizer.class).customize(definition(MachineClient.class), restBuilder);
            verify(restBuilder).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer secret");
            
            WebClient.Builder webBuilder = mock(WebClient.Builder.class);
            context.getBean(WebClientBuilderCustomizer.class).customize(definition(SegmentClient.class), webBuilder);
            verify(webBuilder).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer secret");
        });
    }
    
    @Test
    void leavesOtherClientsAlone() {
        contextRunner("secret").run(context -> {
            RestClient.Builder restBuilder = mock(RestClient.Builder.class);
            context.getBean(RestClientBuilderCustomizer.class).customize(definition(String.class), restBuilder);
            verifyNoInteractions(restBuilder);
        });
    }
    
    @Test
    void sendsNothingWithoutToken() {
        contextRunner(" ").run(context -> {
            RestClient.Builder restBuilder = mock(RestClient.Builder.class);
            context.getBean(RestClientBuilderCustomizer.class).customize(definition(MachineClient.class), restBuilder);
            verifyNoInteractions(restBuilder);
        });
    }
}
