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

import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_OFFSET;
import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_STEP;

import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;


/**
 * @author ahoo wang
 */
class SpringRedisIdSegmentDistributorTest {
    StringRedisTemplate stringRedisTemplate;
    SpringRedisIdSegmentDistributor springRedisIdSegmentDistributor;
    
    @BeforeEach
    private void initRedis() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        lettuceConnectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        SpringRedisIdSegmentDistributorFactory distributorFactory = new SpringRedisIdSegmentDistributorFactory(stringRedisTemplate);
        springRedisIdSegmentDistributor = (SpringRedisIdSegmentDistributor) distributorFactory.create(
            new IdSegmentDistributorDefinition("SpringRedisIdSegmentDistributorTest", MockIdGenerator.INSTANCE.generateAsString(), DEFAULT_OFFSET, DEFAULT_STEP));
    }
    
    @Test
    void nextMaxId() {
        long nextMaxId = springRedisIdSegmentDistributor.nextMaxId();
        Assertions.assertEquals(springRedisIdSegmentDistributor.getStep(), nextMaxId);
    }
    
    @Test
    public void generateWhenMaxIdBack() {
        long id = springRedisIdSegmentDistributor.nextMaxId();
        Assertions.assertTrue(id > 0);
        String adderKey = springRedisIdSegmentDistributor.getAdderKey();
        stringRedisTemplate.opsForValue().set(adderKey, String.valueOf(id - 1));
        Assertions.assertThrows(IllegalStateException.class, () -> {
            springRedisIdSegmentDistributor.nextMaxId();
        });
    }
}
