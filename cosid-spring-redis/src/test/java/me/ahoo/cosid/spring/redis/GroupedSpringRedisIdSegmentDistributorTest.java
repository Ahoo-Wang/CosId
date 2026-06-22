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

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.grouped.GroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupedIdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.date.YearGroupBySupplier;

import org.junit.jupiter.api.Test;

class GroupedSpringRedisIdSegmentDistributorTest {

    @Test
    void groupedFactoryShouldDelegateToSpringRedisFactoryWithoutRedisConnection() {
        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        GroupBySupplier groupBySupplier = new YearGroupBySupplier("yyyy");
        GroupedIdSegmentDistributorFactory factory = new GroupedIdSegmentDistributorFactory(
            groupBySupplier,
            new SpringRedisIdSegmentDistributorFactory(redisTemplate)
        );

        IdSegmentDistributor distributor = factory.create(new IdSegmentDistributorDefinition("group-ns", "orders", 0, 100));
        IdSegment segment = distributor.nextIdSegment();

        String groupedName = "orders@" + groupBySupplier.get().getKey();
        assertThat(segment.getMaxId(), equalTo(100L));
        assertThat(redisTemplate.getSetIfAbsentCalls().get(0).getKey(), equalTo("cosid:{group-ns." + groupedName + "}.adder"));
        assertThat(redisTemplate.getIncrementCalls().get(0).getKey(), equalTo("cosid:{group-ns." + groupedName + "}.adder"));
    }
}
