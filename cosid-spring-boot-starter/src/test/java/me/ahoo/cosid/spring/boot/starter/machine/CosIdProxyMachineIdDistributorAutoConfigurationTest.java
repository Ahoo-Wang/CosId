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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.coapi.spring.client.sync.RestClientBuilderCustomizer;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.proxy.ProxyMachineLeaseStore;
import me.ahoo.cosid.proxy.api.MachineLeaseClient;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.ProxyClientCondition;
import me.ahoo.cosid.spring.boot.starter.ProxyProperties;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

class CosIdProxyMachineIdDistributorAutoConfigurationTest {
    private static final String COAP_PROXY_HOST = "cosid.proxy.host=http://localhost:8688";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdProxyMachineIdDistributorAutoConfiguration.class))
        .withBean(MachineLeaseClient.class, () -> mock(MachineLeaseClient.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withPropertyValues(COAP_PROXY_HOST);

    @Test
    void createsProxyMachineIdDistributorWhenTypeIsProxy() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                    .hasSingleBean(LeaseStoreMachineIdDistributor.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesProxyMachineIdDistributor() {
        MachineIdDistributor userDistributor = mock(MachineIdDistributor.class);

        this.contextRunner
            .withBean(MachineIdDistributor.class, () -> userDistributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .hasSingleBean(MachineIdDistributor.class)
                .getBean(MachineIdDistributor.class)
                .isSameAs(userDistributor));
    }

    @Test
    void zeroReadTimeoutFailsStartupNamingTheProperty() {
        this.contextRunner
            .withBean(CosIdProperties.class, () -> {
                CosIdProperties properties = new CosIdProperties();
                properties.getProxy().setReadTimeout(Duration.ZERO);
                return properties;
            })
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("cosid.proxy.read-timeout"));
    }

    @Test
    void doesNotCreateDistributorWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateDistributorWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=manual")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdProxyMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void failsFastWhenCoApiRestClientBuilderIsMissing() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxyMachineIdDistributorAutoConfiguration.class))
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            // spring-webflux is on the test classpath (for the WebClient timeout test): pin the blocking client mode.
            .withPropertyValues("coapi.mode=SYNC")
            .withPropertyValues(COAP_PROXY_HOST)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(hasCauseMessage(context.getStartupFailure(), "RestClient$Builder"))
                .isTrue());
    }

    private static ApplicationContextRunner plainRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxyMachineIdDistributorAutoConfiguration.class))
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(COAP_PROXY_HOST)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=proxy");
    }
    
    @Test
    void httpInterfaceModeCreatesAPlainClientBean() {
        plainRunner()
            .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=http-interface")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasBean("cosIdProxyMachineLeaseClient")
                .hasSingleBean(MachineLeaseClient.class)
                .doesNotHaveBean(RestClientBuilderCustomizer.class)
                .hasSingleBean(LeaseStoreMachineIdDistributor.class));
    }
    
    @Test
    void withoutCoApiTheClientIsAPlainBean() {
        plainRunner()
            .withClassLoader(new FilteredClassLoader("me.ahoo.coapi.spring"))
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasBean("cosIdProxyMachineLeaseClient")
                .hasSingleBean(LeaseStoreMachineIdDistributor.class));
    }
    
    @Test
    void thePlainClientSendsTheTokenToTheHostWithinTheTimeouts() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/machines/v2/time", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] body = "42".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            plainRunner()
                .withBean(CosIdProperties.class, () -> {
                    CosIdProperties properties = new CosIdProperties();
                    properties.getProxy().setHost(host).setToken("secret").setClient(ProxyProperties.Client.HTTP_INTERFACE);
                    return properties;
                })
                .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=http-interface")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MachineLeaseClient.class).serverTime()).isEqualTo(42L);
                    assertThat(authorization.get()).isEqualTo("Bearer secret");
                });
        } finally {
            server.stop(0);
        }
    }
    
    private static boolean hasCauseMessage(Throwable throwable, String message) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
