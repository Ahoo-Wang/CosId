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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.proxy.ProxyIdSegmentDistributorFactory;
import me.ahoo.cosid.proxy.server.ProxyServer;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs {@link IdSegmentDistributorSpec} through {@link ProxyIdSegmentDistributorFactory} against a real proxy server.
 */
@EnabledIf("me.ahoo.cosid.proxy.server.contract.ProxyEndToEnd#isAvailable")
@SpringBootTest(classes = ProxyServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyIdSegmentDistributorEndToEndTest extends IdSegmentDistributorSpec {
    @LocalServerPort
    int port;
    @Autowired
    StringRedisTemplate redisTemplate;
    IdSegmentDistributorFactory factory;
    
    @DynamicPropertySource
    static void serverProperties(DynamicPropertyRegistry registry) {
        ProxyEndToEnd.serverProperties(registry);
    }
    
    @BeforeEach
    void setup() {
        factory = new ProxyIdSegmentDistributorFactory(ProxyEndToEnd.segmentClient(port));
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return factory;
    }
    
    /**
     * Moves the server's Redis counter, see {@code SpringRedisIdSegmentDistributor#getAdderKey()}.
     */
    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
        redisTemplate.opsForValue().set(CosId.COSID + ":{" + distributor.getNamespacedName() + "}.adder", String.valueOf(maxId));
    }
    
}
