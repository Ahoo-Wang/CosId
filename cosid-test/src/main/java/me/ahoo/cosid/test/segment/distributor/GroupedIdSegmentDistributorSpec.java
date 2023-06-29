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

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentChain;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.DateGroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupBySupplier;
import me.ahoo.cosid.segment.grouped.GroupedIdSegmentDistributorFactory;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Test;

public abstract class GroupedIdSegmentDistributorSpec extends IdSegmentDistributorSpec {
    
    protected GroupBySupplier groupedSupplier() {
        return DateGroupBySupplier.YEAR;
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
    public void nextIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegment", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long expectedMaxId = TEST_OFFSET + TEST_STEP;
        IdSegment actual = distributor.nextIdSegment();
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), equalTo(groupedSupplier().get().ttl()));
    }
    
    @Test
    @Override
    public void nextIdSegmentChain() {
        IdSegmentChain root = IdSegmentChain.newRoot(false);
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextIdSegmentChain", TEST_OFFSET, TEST_STEP);
        IdSegmentDistributor distributor = factory().create(definition);
        long expectedMaxId = TEST_OFFSET + Math.multiplyExact(TEST_STEP, DEFAULT_SEGMENTS);
        IdSegment actual = distributor.nextIdSegmentChain(root);
        assertThat(actual.getMaxId(), equalTo(expectedMaxId));
        assertThat(actual.getStep(), equalTo(TEST_STEP));
        assertThat(actual.getSequence(), equalTo(0L));
        assertThat(actual.getTtl(), equalTo(groupedSupplier().get().ttl()));
    }
}
