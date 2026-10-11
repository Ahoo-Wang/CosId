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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.segment.grouped.GroupedId;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

class DefaultSegmentIdTest {


    @Test
    void currentShouldStartAsOverflowSegmentUntilFirstAllocation() {
        DefaultSegmentId generator = new DefaultSegmentId(new AtomicIdSegmentDistributor(3));

        assertSame(DefaultIdSegment.OVERFLOW, generator.current());
    }

    @Test
    void generateShouldUseCurrentSegmentUntilOverflowThenFetchNextSegment() {
        RecordingDistributor distributor = new RecordingDistributor(3, GroupedKey.NEVER);
        DefaultSegmentId generator = new DefaultSegmentId(distributor);

        assertEquals(1, generator.generate());
        assertEquals(2, generator.generate());
        assertEquals(3, generator.generate());
        IdSegment firstSegment = generator.current();
        assertEquals(3, firstSegment.getMaxId());

        assertEquals(4, generator.generate());

        assertEquals(2, distributor.nextMaxIdCalls);
        assertEquals(6, generator.current().getMaxId());
    }

    @Test
    void generateShouldBypassSegmentCacheWhenDistributorStepIsOne() {
        GroupedKey group = GroupedKey.forever("tenant-1");
        RecordingDistributor distributor = new RecordingDistributor(SegmentId.ONE_STEP, group);
        DefaultSegmentId generator = new DefaultSegmentId(distributor);

        assertEquals(1, generator.generate());
        assertEquals(GroupedId.of(group, 2), generator.generateGrouped());

        assertSame(DefaultIdSegment.OVERFLOW, generator.current());
        assertEquals(2, distributor.nextMaxIdCalls);
    }

    @Test
    void generateAsStringShouldUseDefaultRadix62Converter() {
        DefaultSegmentId generator = new DefaultSegmentId(new AtomicIdSegmentDistributor(3));

        String id = generator.generateAsString();

        assertEquals(1, Radix62IdConverter.PAD_START.asLong(id));
    }

    @Test
    void multipleInstancesSharingDistributorShouldGenerateGloballyUniqueIds() {
        IdSegmentDistributor distributor = new AtomicIdSegmentDistributor(2);
        DefaultSegmentId first = new DefaultSegmentId(distributor);
        DefaultSegmentId second = new DefaultSegmentId(distributor);
        long[] ids = new long[]{
            first.generate(),
            second.generate(),
            first.generate(),
            second.generate()
        };

        Arrays.sort(ids);

        assertArrayEquals(new long[]{1, 2, 3, 4}, ids);
    }

    @Test
    void constructorShouldRejectNonPositiveTtl() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new DefaultSegmentId(0, new AtomicIdSegmentDistributor())
        );

        assertEquals("idSegmentTtl:[0] must be greater than 0.", error.getMessage());
    }

    @Test
    void generateShouldRemainUniqueUnderSmallConcurrentLoad() {
        DefaultSegmentId generator = new DefaultSegmentId(new AtomicIdSegmentDistributor(64));

        new ConcurrentGenerateSpec(4, 256, Duration.ofSeconds(5), generator).verify();
        assertTrue(generator.current().getMaxId() >= 256);
    }

    private static final class RecordingDistributor implements IdSegmentDistributor {
        private final long step;
        private final GroupedKey group;
        private final AtomicLong maxId = new AtomicLong();
        private int nextMaxIdCalls;

        private RecordingDistributor(long step, GroupedKey group) {
            this.step = step;
            this.group = group;
        }

        @Override
        public String getNamespace() {
            return "test";
        }

        @Override
        public String getName() {
            return "segment";
        }

        @Override
        public long getStep() {
            return step;
        }

        @Override
        public GroupedKey group() {
            return group;
        }

        @Override
        public long nextMaxId(long step) {
            nextMaxIdCalls++;
            return maxId.addAndGet(step);
        }
    }
}
