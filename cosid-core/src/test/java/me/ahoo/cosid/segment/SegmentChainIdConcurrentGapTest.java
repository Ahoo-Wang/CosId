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

package me.ahoo.cosid.segment;

import me.ahoo.cosid.segment.grouped.GroupedIdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.date.YearGroupBySupplier;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;

/**
 * Threads that find no usable segment at once append one each: none of them may move the head past a segment another
 * appended meanwhile, whose ids would never be issued. 4.0.2 skipped them, within a few hundred rounds of these.
 */
class SegmentChainIdConcurrentGapTest {
    private static final int ROUNDS = 1000;
    private static final long STEP = 100;

    @Test
    void concurrentGenerationIssuesEveryIdOfEverySegment() {
        for (int round = 0; round < ROUNDS; round++) {
            new ConcurrentGenerateSpec(new SegmentChainId(new AtomicIdSegmentDistributor(STEP))).verify();
        }
    }

    @Test
    void concurrentGroupedGenerationIssuesEveryIdOfEverySegment() {
        IdSegmentDistributorFactory factory = new GroupedIdSegmentDistributorFactory(new YearGroupBySupplier("yyyy"),
            definition -> new AtomicIdSegmentDistributor(definition.getStep()));
        for (int round = 0; round < ROUNDS; round++) {
            IdSegmentDistributor distributor = factory.create(new IdSegmentDistributorDefinition("gap" + round, "order", 0, STEP));
            new ConcurrentGenerateSpec(new SegmentChainId(distributor)).verify();
        }
    }
}
