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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.snowflake.CountedSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class CountedIdGeneratorTest {
    
    @Test
    void countsTheSegmentIdsAndDelegates() throws Exception {
        PrefetchWorkerExecutorService executor = new PrefetchWorkerExecutorService(Duration.ofSeconds(1), 1, false);
        try {
            SegmentChainId chain = new SegmentChainId(IdSegment.TIME_TO_LIVE_FOREVER, 2, 8, new AtomicIdSegmentDistributor(10), executor);
            CountedSegmentId counted = new CountedSegmentId(chain);
            counted.generate();
            counted.generateGrouped();
            counted.generateAsString();
            
            assertThat(counted.getGenerated(), equalTo(3L));
            assertThat(counted.getActual(), sameInstance(chain));
            assertThat(counted.current(), sameInstance(chain.current()));
            assertThat(counted.stat().getKind(), equalTo("CountedSegmentId"));
            counted.close();
            assertThat(chain.isClosed(), is(true));
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    void countsTheSnowflakeIdsAndDelegates() {
        SnowflakeId snowflake = new MillisecondSnowflakeId(1);
        CountedSnowflakeId counted = new CountedSnowflakeId(snowflake);
        counted.generate();
        counted.generate();
        
        assertThat(counted.getGenerated(), equalTo(2L));
        assertThat(counted.getMachineId(), equalTo(1));
        assertThat(counted.getLastTimestamp(), equalTo(snowflake.getLastTimestamp()));
        assertThat(counted.getLastTimestampAsMilliseconds(), equalTo(snowflake.getLastTimestampAsMilliseconds()));
        assertThat(counted.layout(), equalTo(snowflake.layout()));
        assertThat(counted.stat().getKind(), equalTo("CountedSnowflakeId"));
    }
    
    @Test
    void theRemainingIdsOfSegmentGenerators() {
        DefaultSegmentId segment = new DefaultSegmentId(new AtomicIdSegmentDistributor(10));
        segment.generate();
        assertThat(segment.getRemaining(), equalTo(9L));
        AtomicIdSegmentDistributor distributor = new AtomicIdSegmentDistributor(10);
        assertThat(new DefaultSegmentId(distributor).getNamespacedName(), equalTo(distributor.getNamespacedName()));
    }
}
