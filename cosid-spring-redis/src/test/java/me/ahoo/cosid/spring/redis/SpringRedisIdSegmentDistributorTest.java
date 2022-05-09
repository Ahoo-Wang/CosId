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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author ahoo wang
 */
class SpringRedisIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    StringRedisTemplate stringRedisTemplate;
    SpringRedisIdSegmentDistributorFactory distributorFactory;
    protected IdSegmentDistributorDefinition idSegmentDistributorDefinition;

    @BeforeEach
    private void setup() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        lettuceConnectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        distributorFactory = new SpringRedisIdSegmentDistributorFactory(stringRedisTemplate);
        idSegmentDistributorDefinition = new IdSegmentDistributorDefinition("RedisIdSegmentDistributorFactoryTest", MockIdGenerator.INSTANCE.generateAsString(), 0, 100);
    }


    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }

    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
        String adderKey = ((SpringRedisIdSegmentDistributor) distributor).getAdderKey();
        stringRedisTemplate.opsForValue().set(adderKey, String.valueOf(maxId - 1));
    }


    @Test
    protected void distributorFactoryTest() {
        IdSegmentDistributor idSegmentDistributor = distributorFactory.create(idSegmentDistributorDefinition);
        Assertions.assertNotNull(idSegmentDistributor);
    }

    @Test
    void getAdderKey() {
        IdSegmentDistributor idSegmentDistributor = distributorFactory.create(idSegmentDistributorDefinition);
        Assertions.assertTrue(idSegmentDistributor instanceof SpringRedisIdSegmentDistributor);
        SpringRedisIdSegmentDistributor springRedisIdSegmentDistributor=(SpringRedisIdSegmentDistributor)idSegmentDistributor;
        Assertions.assertNotNull(springRedisIdSegmentDistributor.getAdderKey());
        Assertions.assertNotNull(springRedisIdSegmentDistributor.getName());
        Assertions.assertTrue(springRedisIdSegmentDistributor.getOffset()==0);
        Assertions.assertTrue(springRedisIdSegmentDistributor.getStep()==100);
        Assertions.assertNotNull(springRedisIdSegmentDistributor.getNamespace());
        Assertions.assertNotNull(springRedisIdSegmentDistributor.nextMaxId(100));
    }
}
