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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.segment.grouped.GroupedAccessor;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DefaultIdSegmentTest {

    @AfterEach
    void clearGroupedContext() {
        GroupedAccessor.clear();
    }

    @Test
    void constructorShouldExposeRangeTtlAndGroupMetadata() {
        GroupedKey group = GroupedKey.forever("tenant-1");
        DefaultIdSegment segment = new DefaultIdSegment(20, 5, 100, IdSegment.TIME_TO_LIVE_FOREVER, group);

        assertEquals(20, segment.getMaxId());
        assertEquals(15, segment.getOffset());
        assertEquals(15, segment.getSequence());
        assertEquals(5, segment.getStep());
        assertEquals(100, segment.getFetchTime());
        assertEquals(IdSegment.TIME_TO_LIVE_FOREVER, segment.getTtl());
        assertSame(group, segment.group());
        assertTrue(segment.isAvailable());
    }

    @Test
    void constructorShouldRejectNonPositiveTtl() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new DefaultIdSegment(10, 5, 100, 0, GroupedKey.NEVER)
        );

        assertEquals("ttl:[0] must be greater than 0.", error.getMessage());
    }

    @Test
    void incrementAndGetShouldAllocateTheOpenClosedRangeAndThenOverflow() {
        DefaultIdSegment segment = new DefaultIdSegment(3, 3);

        assertEquals(1, segment.incrementAndGet());
        assertEquals(2, segment.incrementAndGet());
        assertEquals(3, segment.incrementAndGet());
        assertEquals(IdSegment.SEQUENCE_OVERFLOW, segment.incrementAndGet());
        assertTrue(segment.isOverflow());
        assertFalse(segment.isAvailable());
    }

    @Test
    void incrementAndGetShouldPublishGroupWhenSegmentIsGrouped() {
        GroupedKey group = GroupedKey.forever("2024");
        DefaultIdSegment segment = new DefaultIdSegment(2, 2, 100, IdSegment.TIME_TO_LIVE_FOREVER, group);

        assertEquals(1, segment.incrementAndGet());

        assertSame(group, GroupedAccessor.requiredGet());
    }

    @Test
    void foreverSegmentShouldNeverExpireAndExpiredSegmentShouldBeUnavailable() {
        DefaultIdSegment forever = new DefaultIdSegment(10, 10, 0, IdSegment.TIME_TO_LIVE_FOREVER, GroupedKey.NEVER);
        DefaultIdSegment expired = new DefaultIdSegment(10, 10, 0, 1, GroupedKey.NEVER);

        assertFalse(forever.isExpired());
        assertTrue(expired.isExpired());
        assertFalse(expired.isAvailable());
    }

    @Test
    void incrementAndGetShouldRemainUniqueUnderSmallConcurrentLoad() {
        DefaultIdSegment segment = new DefaultIdSegment(128, 128);

        new ConcurrentGenerateSpec(4, 128, Duration.ofSeconds(5), segment::incrementAndGet).verify();
        assertTrue(segment.isOverflow());
    }
}
