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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentChain;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class DefaultGroupedIdSegmentDistributorTest {

    @Test
    void metadataShouldComeFromOriginalDefinitionAndCurrentGroupSupplier() {
        MutableGroupBySupplier groupBySupplier = new MutableGroupBySupplier(GroupedKey.forever("group-1"));
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition("ns", "name", 10, 5);
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(
            groupBySupplier,
            definition,
            new RecordingFactory()
        );

        assertEquals("ns", distributor.getNamespace());
        assertEquals("name", distributor.getName());
        assertEquals(5, distributor.getStep());
        assertSame(groupBySupplier, distributor.groupBySupplier());
        assertEquals(GroupedKey.forever("group-1"), distributor.group());
    }

    @Test
    void nextMaxIdShouldKeepIndependentStateForEachGroupWhenGroupSwitchesBack() {
        MutableGroupBySupplier groupBySupplier = new MutableGroupBySupplier(GroupedKey.forever("group-1"));
        RecordingFactory factory = new RecordingFactory();
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(
            groupBySupplier,
            new IdSegmentDistributorDefinition("ns", "order", 0, 1),
            factory
        );

        assertEquals(1, distributor.nextMaxId(1));
        assertEquals(2, distributor.nextMaxId(1));
        groupBySupplier.setGroup(GroupedKey.forever("group-2"));
        assertEquals(1, distributor.nextMaxId(1));
        groupBySupplier.setGroup(GroupedKey.forever("group-1"));
        assertEquals(3, distributor.nextMaxId(1));

        assertEquals(List.of("order@group-1", "order@group-2"), factory.createdNames);
    }

    @Test
    void nextIdSegmentShouldAttachCurrentGroupAndRespectRequestedTtl() {
        GroupedKey group = GroupedKey.forever("2024");
        MutableGroupBySupplier groupBySupplier = new MutableGroupBySupplier(group);
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(
            groupBySupplier,
            new IdSegmentDistributorDefinition("ns", "invoice", 0, 10),
            new RecordingFactory()
        );

        IdSegment segment = distributor.nextIdSegment(7);

        assertEquals(10, segment.getMaxId());
        assertEquals(7, segment.getTtl());
        assertEquals(group, segment.group());
    }

    @Test
    void nextIdSegmentChainShouldUseCurrentGroupAndAllowResetForGroupedSegments() {
        GroupedKey group = GroupedKey.forever("2024");
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(
            new MutableGroupBySupplier(group),
            new IdSegmentDistributorDefinition("ns", "invoice", 0, 10),
            new RecordingFactory()
        );

        IdSegmentChain chain = distributor.nextIdSegmentChain(IdSegmentChain.newRoot(distributor.allowReset()), 2, IdSegment.TIME_TO_LIVE_FOREVER);

        assertTrue(distributor.allowReset());
        assertEquals(20, chain.getMaxId());
        assertEquals(group, chain.group());
    }

    private static final class MutableGroupBySupplier implements GroupBySupplier {
        private GroupedKey group;

        private MutableGroupBySupplier(GroupedKey group) {
            this.group = group;
        }

        private void setGroup(GroupedKey group) {
            this.group = group;
        }

        @Override
        public GroupedKey get() {
            return group;
        }
    }

    private static final class RecordingFactory implements IdSegmentDistributorFactory {
        private final List<String> createdNames = new ArrayList<>();

        @Override
        public IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
            createdNames.add(definition.getName());
            return new RecordingDistributor(definition);
        }
    }

    private static final class RecordingDistributor implements IdSegmentDistributor {
        private final IdSegmentDistributorDefinition definition;
        private final AtomicLong maxId = new AtomicLong();

        private RecordingDistributor(IdSegmentDistributorDefinition definition) {
            this.definition = definition;
        }

        @Override
        public String getNamespace() {
            return definition.getNamespace();
        }

        @Override
        public String getName() {
            return definition.getName();
        }

        @Override
        public long getStep() {
            return definition.getStep();
        }

        @Override
        public long nextMaxId(long step) {
            return maxId.addAndGet(step);
        }
    }
}
