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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.ConcurrentGenerateStingSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class DefaultSegmentIdTest {
    
    @Test
    void generate() {
        DefaultSegmentId defaultSegmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        Assertions.assertTrue(defaultSegmentId.generate() > 0);
    }
    
    @Test
    void current() {
        DefaultSegmentId defaultSegmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        assertThat(defaultSegmentId.current(), equalTo(DefaultIdSegment.OVERFLOW));
    }
    
    @Test
    void generateWhenConcurrent() {
        DefaultSegmentId defaultSegmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        new ConcurrentGenerateSpec(defaultSegmentId)
            .verify();
    }
    
    @Test
    void generateWhenMultiInstanceConcurrent() {
        IdSegmentDistributor testMaxIdDistributor = new IdSegmentDistributor.Mock();
        new ConcurrentGenerateSpec(new DefaultSegmentId(testMaxIdDistributor), new DefaultSegmentId(testMaxIdDistributor))
            .verify();
    }
    
    @Test
    public void generateWhenConcurrentString() {
        IdSegmentDistributor testMaxIdDistributor = new IdSegmentDistributor.Mock();
        new ConcurrentGenerateStingSpec(new DefaultSegmentId(testMaxIdDistributor)).verify();
    }
}
