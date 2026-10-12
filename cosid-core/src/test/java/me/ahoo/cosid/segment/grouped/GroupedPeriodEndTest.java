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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;
import me.ahoo.cosid.util.Clock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class GroupedPeriodEndTest {
    
    /**
     * In a group's last second the time left is 0: fetching a segment used to fail with "ttl:[0] must be greater than 0".
     */
    @Test
    void aSegmentFetchedInTheLastSecondOfItsGroupExpiresWithIt() {
        long now = Clock.SYSTEM.secondTime();
        GroupedKey lastSecond = new GroupedKey("today", now);
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(() -> lastSecond,
            new IdSegmentDistributorDefinition("ns", "order", 0, 10), definition -> new AtomicIdSegmentDistributor());
        
        IdSegment segment = distributor.nextIdSegment(IdSegment.TIME_TO_LIVE_FOREVER);
        assertThat(segment.getTtl() <= 1, is(true));
        assertThat(segment.group().getKey(), equalTo("today"));
    }
    
    /**
     * A key computed just before its group ended is not used: the supplier is asked again for the next group.
     */
    @Test
    void anEndedGroupRollsToTheNextOne() {
        long now = Clock.SYSTEM.secondTime();
        AtomicInteger calls = new AtomicInteger();
        GroupBySupplier supplier = () -> calls.incrementAndGet() == 2
            ? new GroupedKey("yesterday", now - 1)
            : new GroupedKey("today", now + 3600);
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(supplier,
            new IdSegmentDistributorDefinition("ns", "order", 0, 10), definition -> new AtomicIdSegmentDistributor());
        
        IdSegment segment = distributor.nextIdSegment(IdSegment.TIME_TO_LIVE_FOREVER);
        assertThat(segment.group().getKey(), equalTo("today"));
    }
}
