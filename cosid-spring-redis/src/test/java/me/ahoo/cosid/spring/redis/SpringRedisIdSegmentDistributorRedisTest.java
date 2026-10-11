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
import me.ahoo.cosid.segment.IdSegmentRollbackException;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Runs {@link SpringRedisIdSegmentDistributor} ({@code redis_id_generate.lua}) against a real Redis.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringRedisIdSegmentDistributorRedisTest {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    void setup() {
        connectionFactory = RealRedis.createConnectionFactory();
        redisTemplate = RealRedis.createRedisTemplate(connectionFactory);
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

    @Test
    void nextMaxIdShouldStayExactAboveTwoToThe53() {
        // 2^53 + 1 is the first integer a double cannot represent.
        long offset = 1_800_000_000_000_000_001L;
        long step = 100_000_000_000_000_001L;
        SpringRedisIdSegmentDistributor distributor = create(offset, step);

        assertThat(distributor.nextMaxId(), equalTo(offset + step));
        assertThat(distributor.nextMaxId(), equalTo(offset + 2 * step));
        assertThat(redisTemplate.opsForValue().get(distributor.getAdderKey()), equalTo(String.valueOf(offset + 2 * step)));
    }

    @Test
    void nextMaxIdShouldRestoreLargeOffsetExactlyWhenAdderIsLost() {
        long offset = 100_000_000_000_000_007L;
        SpringRedisIdSegmentDistributor distributor = create(offset, 3);
        redisTemplate.delete(distributor.getAdderKey());

        assertThat(distributor.nextMaxId(), equalTo(offset + 3));
    }

    @Test
    void constructorShouldBuildNamespacedAdderKey() {
        SpringRedisIdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor("segment-ns", "orders", 7, 50, redisTemplate);

        assertThat(distributor.getNamespacedName(), equalTo("segment-ns.orders"));
        assertThat(distributor.getAdderKey(), equalTo("cosid:{segment-ns.orders}.adder"));
        assertThat(distributor.getOffset(), equalTo(7L));
        assertThat(distributor.getStep(), equalTo(50L));
    }

    @Test
    void ensureOffsetShouldInitializeAdderOnlyWhenAbsent() {
        SpringRedisIdSegmentDistributor distributor = create(11, 100);
        redisTemplate.opsForValue().set(distributor.getAdderKey(), "500");

        distributor.ensureOffset();

        assertThat(redisTemplate.opsForValue().get(distributor.getAdderKey()), equalTo("500"));
    }

    @Test
    void factoryCreateShouldEnsureOffset() {
        SpringRedisIdSegmentDistributor distributor = create(13, 64);

        assertThat(redisTemplate.opsForValue().get(distributor.getAdderKey()), equalTo("13"));
    }

    @Test
    void nextMaxIdShouldIncrementAdderByRequestedStep() {
        SpringRedisIdSegmentDistributor distributor = create(5, 100);

        assertThat(distributor.nextMaxId(25), equalTo(30L));
        assertThat(redisTemplate.opsForValue().get(distributor.getAdderKey()), equalTo("30"));
    }

    @Test
    void nextMaxIdShouldRejectRedisRollback() {
        SpringRedisIdSegmentDistributor distributor = create(0, 100);
        assertThat(distributor.nextMaxId(), equalTo(100L));
        redisTemplate.opsForValue().set(distributor.getAdderKey(), "50");

        IdSegmentRollbackException exception = Assertions.assertThrows(IdSegmentRollbackException.class, distributor::nextMaxId);
        assertThat(exception.getMessage(), equalTo("nextMaxId:[150] must be greater than nextMinMaxId:[200]!"));
    }
}
