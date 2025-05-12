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

package me.ahoo.cosid.proxy;

import me.ahoo.cosid.proxy.api.MachineApi;
import me.ahoo.cosid.proxy.api.SegmentApi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class ProxyServerLauncher {

    static final Network NETWORK_CONTAINER = Network.newNetwork();
    static final GenericContainer REDIS_CONTAINER;
    static final GenericContainer COSID_PROXY_CONTAINER;
    static final String COSID_PROXY_HOST;

    static {
        REDIS_CONTAINER = new GenericContainer(DockerImageName.parse("redis:latest"))
            .withNetwork(NETWORK_CONTAINER)
            .withNetworkAliases("redis")
            .withReuse(true);
        REDIS_CONTAINER.start();

        int cosidProxyExposedPort = 8688;
        COSID_PROXY_CONTAINER = new GenericContainer(DockerImageName.parse("ahoowang/cosid-proxy:2.5.0"))
            .withNetwork(NETWORK_CONTAINER)
            .withExposedPorts(cosidProxyExposedPort)
            .withReuse(true)
            .withEnv("SPRING_DATA_REDIS_URL", "redis://redis:6379")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

        COSID_PROXY_CONTAINER.start();
        COSID_PROXY_HOST = "http://localhost:" + COSID_PROXY_CONTAINER.getMappedPort(cosidProxyExposedPort);
    }


    @Test
    void tryStart() {

    }

    private static <T> T createApiClient(Class<T> apiClass) {
        RestClient restClient = RestClient.builder().baseUrl(COSID_PROXY_HOST).build();
        RestClientAdapter exchangeAdapter = RestClientAdapter.create(restClient);
        return HttpServiceProxyFactory.builderFor(exchangeAdapter).build().createClient(apiClass);
    }

    static MachineApi createMachineApi() {
        return createApiClient(MachineApi.class);
    }

    static SegmentApi createSegmentApi() {
        return createApiClient(SegmentApi.class);
    }
}
