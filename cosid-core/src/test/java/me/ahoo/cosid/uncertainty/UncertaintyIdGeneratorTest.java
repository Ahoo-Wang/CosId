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

package me.ahoo.cosid.uncertainty;

import static me.ahoo.cosid.snowflake.MillisecondSnowflakeId.DEFAULT_MACHINE_BIT;
import static me.ahoo.cosid.snowflake.MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT;
import static me.ahoo.cosid.snowflake.MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class UncertaintyIdGeneratorTest {
    private static final int UNCERTAINTY_BITS = 5;
    
    @Test
    void generateGivenSnowflakeId() {
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT - UNCERTAINTY_BITS, DEFAULT_SEQUENCE_BIT, 0);
        UncertaintyIdGenerator idGenerator = new UncertaintyIdGenerator(snowflakeId, UNCERTAINTY_BITS);
        assertThat(idGenerator.uncertaintyBits(), equalTo(UNCERTAINTY_BITS));
        assertThat(idGenerator.originalIdBits(), equalTo(SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS));
        assertThat(idGenerator.uncertaintyBound(), equalTo(~(-1L << UNCERTAINTY_BITS) + 1));
        assertThat(idGenerator.maxOriginalId(), equalTo(~(-1L << (SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS))));
        long beforeId = idGenerator.generate();
        long afterId = idGenerator.generate();
        assertThat(beforeId, lessThan(afterId));
    }
    
    @Test
    void generateGivenOverflow() {
        IdGenerator overflowIdGen = new IdGenerator() {
            private final AtomicLong actual = new AtomicLong(~(-1L << (SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS)));
            
            @Override
            public long generate() {
                return actual.getAndIncrement();
            }
        };
        UncertaintyIdGenerator idGenerator = new UncertaintyIdGenerator(overflowIdGen, UNCERTAINTY_BITS);
        idGenerator.generate();
        Assertions.assertThrows(OriginalIdOverflowException.class, idGenerator::generate);
    }
    
    @Test
    void generateGivenSegmentId() {
        DefaultSegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        UncertaintyIdGenerator idGenerator = new UncertaintyIdGenerator(segmentId, UNCERTAINTY_BITS);
        long beforeId = idGenerator.generate();
        long afterId = idGenerator.generate();
        assertThat(beforeId, lessThan(afterId));
    }
    
    @Test
    void generateWhenConcurrentGivenSegmentId() {
        DefaultSegmentId segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        UncertaintyIdGenerator idGenerator = new UncertaintyIdGenerator(segmentId, UNCERTAINTY_BITS);
        new ConcurrentGenerateSpec(idGenerator) {
            @Override
            protected void assertGlobalFirst(long id) {
            }
            
            @Override
            protected void assertGlobalEach(long previousId, long id) {
                Assertions.assertTrue(id > previousId);
            }
            
            @Override
            protected void assertGlobalLast(long lastId) {
            }
        }.verify();
    }
    
    @Test
    void generateWhenConcurrentGivenSnowflakeId() {
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(CosId.COSID_EPOCH, DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT - UNCERTAINTY_BITS, DEFAULT_SEQUENCE_BIT, 0);
        
        UncertaintyIdGenerator idGenerator = new UncertaintyIdGenerator(snowflakeId, UNCERTAINTY_BITS);
        new ConcurrentGenerateSpec(idGenerator) {
            @Override
            protected void assertGlobalFirst(long id) {
            }
            
            @Override
            protected void assertGlobalEach(long previousId, long id) {
                Assertions.assertTrue(id > previousId);
            }
            
            @Override
            protected void assertGlobalLast(long lastId) {
            }
        }.verify();
    }
}
