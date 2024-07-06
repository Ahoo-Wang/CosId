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

package me.ahoo.cosid.segment.grouped;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;

class DefaultGroupedIdSegmentDistributorTest {
    
    @Test
    void ensureGrouped() {
        MockGroupBySupplier groupedSupplier = new MockGroupBySupplier(GroupedKey.forever("group-1"));
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition("ns", "n", 0, 1);
        IdSegmentDistributorFactory actual = definition1 -> new IdSegmentDistributor.Mock();
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(groupedSupplier, definition, actual);
        long maxId1 = distributor.nextMaxId(1);
        assertThat(maxId1, equalTo(1L));
        long maxId2 = distributor.nextMaxId(1);
        assertThat(maxId2, equalTo(2L));
        assertThat(distributor.groupBySupplier().get().getKey(), equalTo("group-1"));
        
        groupedSupplier.setGroup(GroupedKey.forever("group-2"));
        maxId1 = distributor.nextMaxId(1);
        assertThat(maxId1, equalTo(1L));
        maxId2 = distributor.nextMaxId(1);
        assertThat(maxId2, equalTo(2L));
        assertThat(distributor.groupBySupplier().get().getKey(), equalTo("group-2"));
    }
    
    public static class MockGroupBySupplier implements GroupBySupplier {
        private GroupedKey group;
        
        public MockGroupBySupplier(GroupedKey group) {
            this.group = group;
        }
        
        public GroupedKey getGroup() {
            return group;
        }
        
        public MockGroupBySupplier setGroup(GroupedKey group) {
            this.group = group;
            return this;
        }
        
        @Override
        public GroupedKey get() {
            return group;
        }
    }
}