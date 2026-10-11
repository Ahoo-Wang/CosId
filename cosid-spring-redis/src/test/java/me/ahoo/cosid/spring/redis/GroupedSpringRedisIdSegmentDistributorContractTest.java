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

package me.ahoo.cosid.spring.redis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.distributor.GroupedIdSegmentDistributorSpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Runs {@link GroupedIdSegmentDistributorSpec} against a real Redis.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupedSpringRedisIdSegmentDistributorContractTest extends GroupedIdSegmentDistributorSpec {
    LettuceConnectionFactory connectionFactory;
    StringRedisTemplate redisTemplate;
    SpringRedisIdSegmentDistributorFactory distributorFactory;
    
    @BeforeAll
    void setup() {
        connectionFactory = RealRedis.createConnectionFactory();
        redisTemplate = RealRedis.createRedisTemplate(connectionFactory);
        distributorFactory = new SpringRedisIdSegmentDistributorFactory(redisTemplate);
    }
    
    @AfterAll
    void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
    
    @Test
    void groupedDistributorUsesTheGroupedAdderKey() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributor distributor = factory().create(new IdSegmentDistributorDefinition(namespace, "orders", 0, 100));
        
        distributor.nextIdSegment();
        
        String adderKey = "cosid:{" + namespace + ".orders@" + groupedSupplier().get().getKey() + "}.adder";
        assertThat(redisTemplate.opsForValue().get(adderKey), equalTo("100"));
    }
}
