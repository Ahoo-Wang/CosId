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

package me.ahoo.cosid.test.segment.distributor;

import static me.ahoo.cosid.segment.IdSegmentDistributor.DEFAULT_SEGMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.converter.GroupedPrefixIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentChain;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.segment.StringSegmentId;
import me.ahoo.cosid.segment.grouped.GroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupedId;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.segment.grouped.GroupedIdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.date.YearGroupBySupplier;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

public abstract class GroupedIdSegmentDistributorSpec extends IdSegmentDistributorSpec {

    protected GroupBySupplier groupedSupplier() {
        return new YearGroupBySupplier("yyyy");
    }

    @Override
    public void nextMaxIdWhenBack() {

    }

    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {

    }

    @Override
    protected IdSegmentDistributorFactory factory() {
        return new GroupedIdSegmentDistributorFactory(groupedSupplier(), getFactory());
    }

    @Test
    @Override
    public void getGroup() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        String name = "getGroup";
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, name, TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        assertThat(distributor.group(), equalTo(groupedSupplier().get()));
    }

    @Test
    @Override
    public void nextIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegment", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long expectedMaxId = TEST_OFFSET + TEST_STEP;
        final long ttlBefore = groupedSupplier().get().ttl();
        IdSegment actual = distributor.nextIdSegment();
        final long ttlAfter = groupedSupplier().get().ttl();
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), allOf(greaterThanOrEqualTo(ttlAfter), lessThanOrEqualTo(ttlBefore)));
    }

    @Test
    @Override
    public void nextIdSegmentChain() {
        IdSegmentChain root = IdSegmentChain.newRoot(false);
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegmentChain", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long expectedMaxId = TEST_OFFSET + Math.multiplyExact(TEST_STEP, DEFAULT_SEGMENTS);
        final long ttlBefore = groupedSupplier().get().ttl();
        IdSegment actual = distributor.nextIdSegmentChain(root);
        final long ttlAfter = groupedSupplier().get().ttl();
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), allOf(greaterThanOrEqualTo(ttlAfter), lessThanOrEqualTo(ttlBefore)));
    }
    
    @Test
    public void generateGroupedCarriesTheGroup() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateGrouped", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        GroupedKey expected = groupedSupplier().get();
        
        GroupedId segmentId = new DefaultSegmentId(distributor).generateGrouped();
        GroupedId chainId = new SegmentChainId(distributor).generateGrouped();
        
        assertThat(segmentId, equalTo(GroupedId.of(expected, TEST_OFFSET + 1)));
        assertThat(chainId.getGroup(), equalTo(expected));
        assertThat(chainId.getValue(), greaterThan(segmentId.getValue()));
    }
    
    @Test
    public void generateGroupedAsStringNeedsNoThreadLocalGroup() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateGroupedAsString", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        SegmentId segmentId = new StringSegmentId(new DefaultSegmentId(distributor),
            new GroupedPrefixIdConverter("-", ToStringIdConverter.INSTANCE));
        
        assertThat(segmentId.generateGroupedAsString(), equalTo(groupedSupplier().get().getKey() + "-" + (TEST_OFFSET + 1)));
    }
    
    /**
     * A string segment id with a grouped prefix takes the group from the segment the id came from.
     */
    @Test
    public void generateAsStringUsesTheSegmentGroup() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "generateAsString", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        SegmentId segmentId = new StringSegmentId(new DefaultSegmentId(distributor),
            new GroupedPrefixIdConverter("-", ToStringIdConverter.INSTANCE));
        
        assertThat(segmentId.generateAsString(), equalTo(groupedSupplier().get().getKey() + "-" + (TEST_OFFSET + 1)));
    }
    
    /**
     * A group supplier that returns {@code before} once more after being armed, then {@code after}: the group rolls
     * over right after the distributor picked the group of the next id.
     */
    private static final class RollingGroupBySupplier implements GroupBySupplier {
        private final GroupedKey before;
        private final GroupedKey after;
        private final java.util.concurrent.atomic.AtomicInteger beforeLeft = new java.util.concurrent.atomic.AtomicInteger(Integer.MAX_VALUE);
        
        private RollingGroupBySupplier(GroupedKey before, GroupedKey after) {
            this.before = before;
            this.after = after;
        }
        
        void rollOverAfterNextGet() {
            beforeLeft.set(1);
        }
        
        @Override
        public GroupedKey get() {
            return beforeLeft.getAndDecrement() > 0 ? before : after;
        }
    }
    
    /**
     * With step 1 every id comes from its own call to the distributor: the id and its group must come from the same
     * grouped counter even when the group rolls over in between, or "2026-1" could be issued twice.
     */
    @Test
    public void oneStepIdKeepsItsGroupAcrossRollover() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        long ttlAt = groupedSupplier().get().getTtlAt();
        GroupedKey before = new GroupedKey("before", ttlAt);
        RollingGroupBySupplier supplier = new RollingGroupBySupplier(before, new GroupedKey("after", ttlAt));
        IdSegmentDistributor distributor = new GroupedIdSegmentDistributorFactory(supplier, getFactory())
            .create(new IdSegmentDistributorDefinition(namespace, "oneStepRollover", TEST_OFFSET, 1));
        DefaultSegmentId segmentId = new DefaultSegmentId(distributor);
        
        supplier.rollOverAfterNextGet();
        GroupedId grouped = segmentId.generateGrouped();
        
        assertThat(grouped, equalTo(GroupedId.of(before, TEST_OFFSET + 1)));
    }
}
