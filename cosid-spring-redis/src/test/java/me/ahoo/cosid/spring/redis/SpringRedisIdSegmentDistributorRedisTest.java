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

import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Runs {@code redis_id_generate.lua} against a real Redis on {@code localhost:6379} (started by CI).
 * Skipped when no Redis is reachable.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringRedisIdSegmentDistributorRedisTest {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    void setup() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        boolean available;
        try {
            redisTemplate.hasKey("cosid:ping");
            available = true;
        } catch (RuntimeException exception) {
            available = false;
        }
        Assumptions.assumeTrue(available, "Redis is not available on localhost:6379.");
    }

    @AfterAll
    void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private SpringRedisIdSegmentDistributor create(long offset, long step) {
        String name = MockIdGenerator.usePrefix("offset").generateAsString();
        return (SpringRedisIdSegmentDistributor) new SpringRedisIdSegmentDistributorFactory(redisTemplate)
            .create(new IdSegmentDistributorDefinition("redis-test", name, offset, step));
    }

    @Test
    void nextMaxIdShouldStartFromOffset() {
        SpringRedisIdSegmentDistributor distributor = create(1000, 100);

        assertThat(distributor.nextMaxId(), equalTo(1100L));
        assertThat(distributor.nextMaxId(), equalTo(1200L));
    }

    @Test
    void nextMaxIdShouldRestoreOffsetWhenAdderIsLost() {
        SpringRedisIdSegmentDistributor distributor = create(1000, 100);
        redisTemplate.delete(distributor.getAdderKey());

        assertThat(distributor.nextMaxId(), equalTo(1100L));
    }

    @Test
    void nextMaxIdShouldNotResetExistingAdder() {
        SpringRedisIdSegmentDistributor distributor = create(1000, 100);
        redisTemplate.opsForValue().set(distributor.getAdderKey(), "5000");

        assertThat(distributor.nextMaxId(), equalTo(5100L));
    }
}
