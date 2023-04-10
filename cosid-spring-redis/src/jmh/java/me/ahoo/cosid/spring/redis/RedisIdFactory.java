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

import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentChainId;

import io.lettuce.core.RedisClient;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class RedisIdFactory implements AutoCloseable {
    
    public static final RedisIdFactory INSTANCE = new RedisIdFactory();
    
    AtomicInteger counter = new AtomicInteger();
    LettuceConnectionFactory connectionFactory;
    StringRedisTemplate stringRedisTemplate;
    
    private RedisIdFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        connectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
        connectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
    }
    
    public synchronized SpringRedisIdSegmentDistributor createDistributor(int step) {
        String namespace = "rbh-" + counter.incrementAndGet();
        return new SpringRedisIdSegmentDistributor(
            namespace,
            String.valueOf(step),
            0,
            step,
            stringRedisTemplate);
    }
    
    
    public DefaultSegmentId createSegmentId(int step) {
        SpringRedisIdSegmentDistributor distributor = createDistributor(step);
        return new DefaultSegmentId(distributor);
    }
    
    
    public SegmentChainId createSegmentChainId(int step) {
        SpringRedisIdSegmentDistributor distributor = createDistributor(step);
        return new SegmentChainId(distributor);
    }
    
    @Override
    public void close() {
        if (Objects.nonNull(connectionFactory)) {
            connectionFactory.destroy();
        }
    }
}
