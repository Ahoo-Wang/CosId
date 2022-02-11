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

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.test.ConcurrentGenerateTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;


/**
 * @author ahoo wang
 */
class SegmentChainIdTest {
    
    @Test
    void sort() {
        IdSegmentDistributor idSegmentDistributor = new IdSegmentDistributor.Atomic();
        IdSegmentChain idSegmentChain1 = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot());
        IdSegmentChain idSegmentChain2 = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot());
        IdSegmentChain idSegmentChain3 = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot());
        List<IdSegmentChain> chainList = Arrays.asList(idSegmentChain2, idSegmentChain1, idSegmentChain3);
        chainList.sort(null);
        Assertions.assertEquals(idSegmentChain1, chainList.get(0));
        Assertions.assertEquals(idSegmentChain2, chainList.get(1));
        Assertions.assertEquals(idSegmentChain3, chainList.get(2));
    }
    
    @Test
    void nextIdSegmentsChain() {
        IdSegmentDistributor idSegmentDistributor = new IdSegmentDistributor.Atomic();
        IdSegmentChain rootChain = idSegmentDistributor.nextIdSegmentChain(IdSegmentChain.newRoot(), 3, TIME_TO_LIVE_FOREVER);
        Assertions.assertEquals(0, rootChain.getVersion());
        Assertions.assertEquals(0, rootChain.getIdSegment().getOffset());
        Assertions.assertEquals(300, rootChain.getStep());
        Assertions.assertEquals(300, rootChain.getMaxId());
        
    }
    
    @Test
    void generate() {
        SegmentChainId segmentChainId = new SegmentChainId(TIME_TO_LIVE_FOREVER, 10, new IdSegmentDistributor.Atomic(2), PrefetchWorkerExecutorService.DEFAULT);
        segmentChainId.generate();
        segmentChainId.generate();
        segmentChainId.generate();
    }
    
    @Test
    public void concurrent_generate() {
        SegmentChainId segmentChainId = new SegmentChainId(new IdSegmentDistributor.Mock());
        
        new ConcurrentGenerateTest(segmentChainId).assertConcurrentGenerate();
    }
    
    @Test
    public void concurrent_generate_multi_instance() {
        
        IdSegmentDistributor testMaxIdDistributor = new IdSegmentDistributor.Mock();
        new ConcurrentGenerateTest(new SegmentChainId(testMaxIdDistributor), new SegmentChainId(testMaxIdDistributor)) {
            
            @Override
            protected void assertGlobalEach(long previousId, long id) {
                /**
                 * SegmentChainId 预取（安全间隙规则）导致实例1/实例2 预取到的IdSegment没有完全使用，导致ID空洞，只能保证趋势递增
                 */
                Assertions.assertTrue(previousId + 1 <= id);
            }
            
            @Override
            protected void assertGlobalLast(long lastId) {
                Assertions.assertTrue(getMaxId() <= lastId);
            }
        }.assertConcurrentGenerate();
    }
}
