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

package me.ahoo.cosid.proxy.server.contract;

import me.ahoo.cosid.proxy.api.MachineClient;
import me.ahoo.cosid.proxy.api.MachineLeaseClient;
import me.ahoo.cosid.proxy.api.SegmentClient;
import me.ahoo.cosid.test.container.RedisLauncher;

import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Shared setup of the end-to-end contract tests: a real {@code ProxyServer} on a random port, backed by a real Redis,
 * with token authentication on, and the CosId proxy clients pointing at it.
 */
final class ProxyEndToEnd {
    static final String TOKEN = "contract-test-token";
    
    private ProxyEndToEnd() {
    }
    
    /**
     * Used by {@code @EnabledIf}: the tests need Docker or {@code COSID_TEST_REDIS_URI}.
     */
    static boolean isAvailable() {
        return RedisLauncher.isAvailable();
    }
    
    static void serverProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", RedisLauncher::getUri);
        registry.add("cosid.proxy.server.auth.tokens[0]", () -> TOKEN);
    }
    
    private static HttpServiceProxyFactory clientFactory(int port, boolean withToken) {
        RestClient.Builder restClient = RestClient.builder().baseUrl("http://localhost:" + port);
        if (withToken) {
            restClient.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        }
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient.build())).build();
    }
    
    static MachineClient machineClient(int port) {
        return clientFactory(port, true).createClient(MachineClient.class);
    }
    
    static MachineClient anonymousMachineClient(int port) {
        return clientFactory(port, false).createClient(MachineClient.class);
    }
    
    static MachineLeaseClient machineLeaseClient(int port) {
        return clientFactory(port, true).createClient(MachineLeaseClient.class);
    }
    
    static SegmentClient segmentClient(int port) {
        return clientFactory(port, true).createClient(SegmentClient.class);
    }
}
