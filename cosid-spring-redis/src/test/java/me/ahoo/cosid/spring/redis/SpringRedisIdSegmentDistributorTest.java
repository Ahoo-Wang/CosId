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
import static org.hamcrest.Matchers.instanceOf;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SpringRedisIdSegmentDistributorTest {

    @Test
    void constructorShouldBuildNamespacedAdderKey() {
        SpringRedisIdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(
            "segment-ns",
            "orders",
            7,
            50,
            new FakeStringRedisTemplate()
        );

        assertThat(distributor.getNamespace(), equalTo("segment-ns"));
        assertThat(distributor.getName(), equalTo("orders"));
        assertThat(distributor.getNamespacedName(), equalTo("segment-ns.orders"));
        assertThat(distributor.getAdderKey(), equalTo("cosid:{segment-ns.orders}.adder"));
        assertThat(distributor.getOffset(), equalTo(7L));
        assertThat(distributor.getStep(), equalTo(50L));
    }

    @Test
    void ensureOffsetShouldInitializeAdderOnlyWhenAbsent() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        SpringRedisIdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(
            "segment-ns",
            "invoice",
            11,
            100,
            redisTemplate
        );

        distributor.ensureOffset();
        distributor.ensureOffset();

        assertThat(redisTemplate.getSetIfAbsentCalls().size(), equalTo(2));
        FakeStringRedisTemplate.SetIfAbsentCall call = redisTemplate.getSetIfAbsentCalls().get(0);
        assertThat(call.getKey(), equalTo("cosid:{segment-ns.invoice}.adder"));
        assertThat(call.getValue(), equalTo("11"));
        assertThat(redisTemplate.getValue(distributor.getAdderKey()), equalTo(11L));
    }

    @Test
    void nextMaxIdShouldIncrementAdderByRequestedStep() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        SpringRedisIdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(
            "segment-ns",
            "payment",
            5,
            100,
            redisTemplate
        );
        distributor.ensureOffset();

        long nextMaxId = distributor.nextMaxId(25);

        assertThat(nextMaxId, equalTo(30L));
        assertThat(redisTemplate.getIncrementCalls().size(), equalTo(1));
        FakeStringRedisTemplate.IncrementCall incrementCall = redisTemplate.getIncrementCalls().get(0);
        assertThat(incrementCall.getKey(), equalTo("cosid:{segment-ns.payment}.adder"));
        assertThat(incrementCall.getDelta(), equalTo(25L));
        assertThat(redisTemplate.getValue(distributor.getAdderKey()), equalTo(30L));
    }

    @Test
    void nextMaxIdShouldRejectRedisRollback() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        SpringRedisIdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(
            "segment-ns",
            "rollback",
            0,
            100,
            redisTemplate
        );
        distributor.ensureOffset();
        assertThat(distributor.nextMaxId(), equalTo(100L));
        redisTemplate.setValue(distributor.getAdderKey(), 50L);

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, distributor::nextMaxId);
        assertThat(exception.getMessage(), equalTo("nextMaxId:[150] must be greater than nextMinMaxId:[200]!"));
    }

    @Test
    void factoryCreateShouldEnsureOffset() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        SpringRedisIdSegmentDistributorFactory factory = new SpringRedisIdSegmentDistributorFactory(redisTemplate);
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition("factory-ns", "orders", 13, 64);

        IdSegmentDistributor distributor = factory.create(definition);

        assertThat(distributor, instanceOf(SpringRedisIdSegmentDistributor.class));
        assertThat(redisTemplate.getSetIfAbsentCalls().size(), equalTo(1));
        FakeStringRedisTemplate.SetIfAbsentCall call = redisTemplate.getSetIfAbsentCalls().get(0);
        assertThat(call.getKey(), equalTo("cosid:{factory-ns.orders}.adder"));
        assertThat(call.getValue(), equalTo("13"));
    }
}
