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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import me.ahoo.coapi.spring.CoApiDefinition;
import me.ahoo.coapi.spring.client.reactive.WebClientBuilderCustomizer;
import me.ahoo.coapi.spring.client.sync.RestClientBuilderCustomizer;
import me.ahoo.cosid.proxy.api.MachineClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

class CosIdProxyClientTimeoutConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CosIdProxyClientTimeoutConfiguration.class)
        .withBean(CosIdProperties.class, () -> {
            CosIdProperties properties = new CosIdProperties();
            properties.getProxy().setReadTimeout(Duration.ofMillis(300));
            return properties;
        });
    
    /**
     * Accepts connections and never answers, like a proxy server that hangs.
     */
    private ServerSocket hangingServer;
    private final List<Socket> accepted = new ArrayList<>();
    
    @BeforeEach
    void startHangingServer() throws IOException {
        hangingServer = new ServerSocket(0);
        Thread acceptor = new Thread(() -> {
            while (!hangingServer.isClosed()) {
                try {
                    accepted.add(hangingServer.accept());
                } catch (IOException closed) {
                    return;
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }
    
    @AfterEach
    void stopHangingServer() throws IOException {
        hangingServer.close();
        for (Socket socket : accepted) {
            socket.close();
        }
    }
    
    private String hangingUrl() {
        return "http://localhost:" + hangingServer.getLocalPort() + "/hang";
    }
    
    private static CoApiDefinition definition(Class<?> apiType) {
        return new CoApiDefinition(apiType.getSimpleName(), apiType, "http://localhost", false);
    }
    
    @Test
    void restClientOfProxyClientTimesOut() {
        contextRunner.run(context -> {
            RestClient.Builder builder = RestClient.builder();
            context.getBean(RestClientBuilderCustomizer.class).customize(definition(MachineClient.class), builder);
            RestClient restClient = builder.build();
            
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> restClient.get().uri(hangingUrl()).retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
        });
    }
    
    @Test
    void restClientOfOtherClientsIsUntouched() {
        contextRunner.run(context -> {
            RestClient.Builder builder = mock(RestClient.Builder.class);
            context.getBean(RestClientBuilderCustomizer.class).customize(definition(String.class), builder);
            
            verifyNoInteractions(builder);
        });
    }
    
    @Test
    void webClientOfProxyClientTimesOut() {
        contextRunner.run(context -> {
            WebClient.Builder builder = WebClient.builder().clientConnector(new JdkClientHttpConnector());
            context.getBean(WebClientBuilderCustomizer.class).customize(definition(MachineClient.class), builder);
            WebClient webClient = builder.build();
            
            assertThatThrownBy(() -> webClient.get().uri(hangingUrl()).retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5)))
                .hasRootCauseInstanceOf(TimeoutException.class);
        });
    }
    
    @Test
    void keepsApplicationRequestFactoryWhenTimeoutsAreDisabled() {
        new ApplicationContextRunner()
            .withUserConfiguration(CosIdProxyClientTimeoutConfiguration.class)
            .withBean(CosIdProperties.class, () -> {
                CosIdProperties properties = new CosIdProperties();
                properties.getProxy().setConnectTimeout(Duration.ZERO).setReadTimeout(Duration.ZERO);
                return properties;
            })
            .run(context -> {
                RestClient.Builder builder = mock(RestClient.Builder.class);
                context.getBean(RestClientBuilderCustomizer.class).customize(definition(MachineClient.class), builder);
                
                verifyNoInteractions(builder);
            });
    }
}
