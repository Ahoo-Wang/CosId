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

import me.ahoo.cosid.segment.grouped.GroupedAccessor;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.util.Clock;

import com.google.common.base.Preconditions;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Default implementation of an ID segment.
 *
 * <p>This class provides a concrete implementation of the {@link IdSegment}
 * interface, representing a contiguous block of IDs allocated for generation.
 * It maintains state information about the segment and provides thread-safe
 * ID allocation within the segment.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li><strong>Thread-Safe</strong>: Uses atomic operations for ID allocation</li>
 *   <li><strong>Immutable Range</strong>: Offset and max ID are fixed at construction</li>
 *   <li><strong>Expiration Support</strong>: Includes TTL for segment lifecycle management</li>
 *   <li><strong>Overflow Detection</strong>: Automatically detects when segment is exhausted</li>
 * </ul>
 *
 * <p>The segment works by:
 * <ol>
 *   <li>Maintaining an offset (starting ID) and max ID (ending ID)</li>
 *   <li>Tracking the current sequence (next available ID)</li>
 *   <li>Atomically incrementing the sequence when IDs are allocated</li>
 *   <li>Detecting overflow when the sequence exceeds the max ID</li>
 * </ol>
 *
 * @author ahoo wang
 */
public class DefaultIdSegment implements IdSegment {

    /**
     * A sentinel value representing an overflow segment.
     *
     * <p>This is used to signal that a segment has been exhausted and
     * no more IDs can be allocated from it.
     */
    public static final DefaultIdSegment OVERFLOW = new DefaultIdSegment(IdSegment.SEQUENCE_OVERFLOW, 0, Clock.SYSTEM.secondTime(), TIME_TO_LIVE_FOREVER, GroupedKey.NEVER);

    /**
     * The maximum ID in this segment (inclusive).
     *
     * <p>This is the upper bound of the ID range allocated to this segment.
     * IDs generated from this segment will not exceed this value.
     */
    private final long maxId;

    /**
     * The offset (starting ID) of this segment.
     *
     * <p>This is the lower bound of the ID range allocated to this segment,
     * calculated as {@code maxId - step}. The first ID generated from this
     * segment will typically be this value plus one.
     */
    private final long offset;

    /**
     * The step size for ID allocation.
     *
     * <p>This determines how much the sequence number is incremented each
     * time an ID is allocated. It also determines the size of the segment.
     */
    private final long step;

    /**
     * The current sequence number within this segment.
     *
     * <p>This represents the next ID that will be allocated from this segment,
     * or {@link IdSegment#SEQUENCE_OVERFLOW} if the segment has been exhausted.
     *
     * <p>Marked volatile to ensure visibility across threads for atomic operations.
     */
    private volatile long sequence;

    /**
     * The time when this segment was fetched.
     *
     * <p>This timestamp is used for expiration calculations and represents
     * when the segment was allocated from the central distributor.
     */
    private final long fetchTime;

    /**
     * The time-to-live for this segment.
     *
     * <p>This determines how long the segment remains valid before it should
     * be refreshed or replaced.
     */
    private final long ttl;

    /**
     * The group key for this segment.
     *
     * <p>This is used for grouping related segments together, which can be
     * useful for management and monitoring purposes.
     */
    private final GroupedKey group;

    /**
     * Atomic field updater for the sequence field.
     *
     * <p>This provides thread-safe atomic operations on the sequence field
     * without requiring synchronization.
     */
    private static final AtomicLongFieldUpdater<DefaultIdSegment> S = AtomicLongFieldUpdater.newUpdater(DefaultIdSegment.class, "sequence");

    /**
     * Create a new DefaultIdSegment with default configuration.
     *
     * <p>This constructor creates a segment with infinite TTL and no grouping.
     *
     * @param maxId The maximum ID in this segment
     * @param step  The step size for ID allocation
     */
    public DefaultIdSegment(long maxId, long step) {
        this(maxId, step, Clock.SYSTEM.secondTime(), TIME_TO_LIVE_FOREVER, GroupedKey.NEVER);
    }

    /**
     * Create a new DefaultIdSegment with full custom configuration.
     *
     * <p>This constructor allows complete control over all parameters of the
     * ID segment.
     *
     * @param maxId     The maximum ID in this segment
     * @param step      The step size for ID allocation
     * @param fetchTime The time when this segment was fetched
     * @param ttl       The time-to-live for this segment
     * @param group     The group key for this segment
     */
    public DefaultIdSegment(long maxId, long step, long fetchTime, long ttl, GroupedKey group) {
        Preconditions.checkArgument(ttl > 0, "ttl:[%s] must be greater than 0.", ttl);
        this.maxId = maxId;
        this.step = step;
        this.offset = maxId - step;
        this.sequence = offset;
        this.fetchTime = fetchTime;
        this.ttl = ttl;
        this.group = group;
    }

    /**
     * Get the group key for this segment.
     *
     * @return The group key
     */
    @Override
    public GroupedKey group() {
        return group;
    }

    /**
     * Get the time when this segment was fetched.
     *
     * @return The fetch time
     */
    @Override
    public long getFetchTime() {
        return fetchTime;
    }

    /**
     * Get the time-to-live for this segment.
     *
     * @return The time-to-live
     */
    @Override
    public long getTtl() {
        return ttl;
    }

    /**
     * Get the maximum ID in this segment.
     *
     * @return The maximum ID
     */
    @Override
    public long getMaxId() {
        return maxId;
    }

    /**
     * Get the offset (starting ID) of this segment.
     *
     * @return The offset
     */
    @Override
    public long getOffset() {
        return offset;
    }

    /**
     * Get the current sequence number within this segment.
     *
     * @return The current sequence number
     */
    @Override
    public long getSequence() {
        return sequence;
    }

    /**
     * Get the step size for ID allocation.
     *
     * @return The step size
     */
    @Override
    public long getStep() {
        return step;
    }

    /**
     * Atomically increment the sequence and return the new value.
     *
     * <p>This method provides thread-safe allocation of the next ID from
     * this segment. If the segment has been exhausted, it returns
     * {@link IdSegment#SEQUENCE_OVERFLOW}.
     *
     * <p>The method performs the following steps:
     * <ol>
     *   <li>Check if the segment has already overflowed</li>
     *   <li>Atomically increment the sequence number</li>
     *   <li>Check if the new sequence represents an overflow</li>
     *   <li>Update grouped accessor if needed</li>
     *   <li>Return the allocated ID or overflow indicator</li>
     * </ol>
     *
     * @return The next allocated ID, or {@link IdSegment#SEQUENCE_OVERFLOW} if exhausted
     */
    @Override
    public long incrementAndGet() {
        if (isOverflow()) {
            return SEQUENCE_OVERFLOW;
        }

        final long nextSeq = S.incrementAndGet(this);

        if (isOverflow(nextSeq)) {
            return SEQUENCE_OVERFLOW;
        }
        GroupedAccessor.setIfNotNever(group());
        return nextSeq;
    }

    /**
     * Get the string representation of this segment.
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        return "DefaultIdSegment{"
            + "maxId=" + maxId
            + ", offset=" + offset
            + ", step=" + step
            + ", sequence=" + sequence
            + ", fetchTime=" + fetchTime
            + ", ttl=" + ttl
            + '}';
    }
}
