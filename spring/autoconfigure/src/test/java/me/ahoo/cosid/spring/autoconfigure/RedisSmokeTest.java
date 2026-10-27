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

package me.ahoo.cosid.spring.autoconfigure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.UUID;

/**
 * A real backend end to end: a Redis store on the context's {@link LettuceConnectionFactory}, for a segment generator
 * and the machine slot.
 */
class RedisSmokeTest {
    @Test
    void segmentsAndTheSlotOnTheContextsRedis() {
        CosIdAutoConfigurationTest.RUNNER
            .withBean(LettuceConnectionFactory.class, TestRedis::lettuce, definition -> definition.setDestroyMethodName("destroy"))
            .withPropertyValues("cosid.namespace=smoke-" + UUID.randomUUID(), "cosid.stores.cache.type=redis", "cosid.stores.cache.timeout=2s",
                "cosid.machine.leased.store=cache", "cosid.generators.default.strategy=snowflake",
                "cosid.generators.order.strategy=segment", "cosid.generators.order.store=cache")
            .run(context -> {
                assertNull(context.getStartupFailure());
                CosIdRuntime runtime = context.getBean(CosIdRuntime.class);
                assertTrue(runtime.slot().isPresent());
                IdGenerator order = context.getBean("order", IdGenerator.class);
                long first = order.generate();
                assertThat(order.generate(), greaterThan(first));
                assertThat(context.getBean(IdGenerator.class).generate(), greaterThan(0L));
            });
    }
}
