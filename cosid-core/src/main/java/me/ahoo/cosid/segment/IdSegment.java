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

import me.ahoo.cosid.segment.grouped.Grouped;
import me.ahoo.cosid.util.Clock;

import com.google.errorprone.annotations.ThreadSafe;

/**
 * ID segment representing a contiguous block of IDs allocated for generation.
 * 
 * <p>An ID segment is a range of IDs that has been allocated to a specific
 * generator instance for local generation. It contains metadata about the
 * segment's state and provides thread-safe ID allocation within the segment.
 * 
 * <p>Key concepts:
 * <ul>
 *   <li><strong>Offset</strong>: The starting ID of the segment</li>
 *   <li><strong>Max ID</strong>: The ending ID of the segment</li>
 *   <li><strong>Sequence</strong>: The next available ID within the segment</li>
 *   <li><strong>Step</strong>: The increment size for ID allocation</li>
 * </ul>
 * 
 * <p>Segments implement expiration and overflow detection to ensure proper
 * lifecycle management and prevent ID conflicts.
 * 
 * <p><img src="../doc-files/SegmentId.png" alt="SegmentId"></p>
 * 
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdSegment extends Comparable<IdSegment>, Grouped {
    
    /**
     * Special sequence value indicating overflow.
     * 
     * <p>This constant is used to signal that a segment has been exhausted
     * and no more IDs can be allocated from it.
     */
    long SEQUENCE_OVERFLOW = -1;
    
    /**
     * Special TTL value indicating the segment never expires.
     * 
     * <p>This constant is used for segments that should remain valid
     * indefinitely, avoiding the need for expiration checks.
     */
    long TIME_TO_LIVE_FOREVER = Long.MAX_VALUE;
    
    /**
     * Get the time when this segment was fetched.
     * 
     * <p>This timestamp is used for expiration calculations and represents
     * when the segment was allocated from the central distributor.
     * 
     * <p>Unit: {@link java.util.concurrent.TimeUnit#SECONDS}
     *
     * @return The fetch time in seconds
     */
    long getFetchTime();
    
    /**
     * Get the maximum ID in this segment.
     * 
     * <p>This is the upper bound of the ID range allocated to this segment.
     * IDs generated from this segment will not exceed this value.
     *
     * @return The maximum ID in this segment
     */
    long getMaxId();
    
    /**
     * Get the offset (starting ID) of this segment.
     * 
     * <p>This is the lower bound of the ID range allocated to this segment.
     * The first ID generated from this segment will typically be this value
     * or slightly higher depending on the step size.
     *
     * @return The offset of this segment
     */
    long getOffset();
    
    /**
     * Get the current sequence number within this segment.
     * 
     * <p>This represents the next ID that will be allocated from this segment,
     * or {@link #SEQUENCE_OVERFLOW} if the segment has been exhausted.
     *
     * @return The current sequence number
     */
    long getSequence();
    
    /**
     * Get the step size for ID allocation.
     * 
     * <p>This determines how much the sequence number is incremented each
     * time an ID is allocated. A step size of 1 allocates consecutive IDs,
     * while larger step sizes can be used for sharding or other purposes.
     *
     * @return The step size for ID allocation
     */
    long getStep();
    
    /**
     * Get the time-to-live for this segment.
     * 
     * <p>This determines how long the segment remains valid before it should
     * be refreshed or replaced. A value of {@link #TIME_TO_LIVE_FOREVER}
     * indicates the segment never expires.
     * 
     * <p>Unit: {@link java.util.concurrent.TimeUnit#SECONDS}
     *
     * @return The time-to-live in seconds
     */
    default long getTtl() {
        return TIME_TO_LIVE_FOREVER;
    }
    
    /**
     * Check if this segment has expired.
     * 
     * <p>An expired segment should no longer be used for ID generation and
     * should be refreshed or replaced with a new segment.
     * 
     * <p>Segments with {@link #TIME_TO_LIVE_FOREVER} never expire, avoiding
     * the performance cost of clock checks for permanent segments.
     *
     * @return {@code true} if the segment has expired, {@code false} otherwise
     */
    default boolean isExpired() {
        if (TIME_TO_LIVE_FOREVER == getTtl()) {
            /*
             * Very important! Avoid getting the current clock,
             * because getting the clock is too slow.
             */
            return false;
        }
        
        return Clock.SYSTEM.secondTime() - getFetchTime() > getTtl();
    }
    
    /**
     * Check if this segment has overflowed.
     * 
     * <p>An overflowed segment has exhausted its ID range and cannot allocate
     * any more IDs. A new segment should be requested when this occurs.
     *
     * @return {@code true} if the segment has overflowed, {@code false} otherwise
     */
    default boolean isOverflow() {
        return getSequence() >= getMaxId();
    }
    
    /**
     * Check if the specified sequence number represents an overflow.
     * 
     * <p>This method checks if a specific sequence number indicates that
     * the segment has been exhausted, either by matching the overflow
     * constant or by exceeding the maximum ID.
     *
     * @param nextSeq The sequence number to check
     * @return {@code true} if the sequence number indicates overflow, {@code false} otherwise
     */
    default boolean isOverflow(long nextSeq) {
        return nextSeq == SEQUENCE_OVERFLOW || nextSeq > getMaxId();
    }
    
    /**
     * Check if this segment is available for ID generation.
     * 
     * <p>A segment is available if it has not expired and has not overflowed.
     * Available segments can be used for generating new IDs.
     *
     * @return {@code true} if the segment is available, {@code false} otherwise
     */
    default boolean isAvailable() {
        return !isExpired() && !isOverflow();
    }
    
    /**
     * Atomically increment the sequence and return the new value.
     * 
     * <p>This method provides thread-safe allocation of the next ID from
     * this segment. If the segment has been exhausted, it returns
     * {@link #SEQUENCE_OVERFLOW}.
     *
     * @return The next allocated ID, or {@link #SEQUENCE_OVERFLOW} if exhausted
     */
    long incrementAndGet();
    
    /**
     * Compare this segment to another segment based on offset.
     * 
     * <p>Segments are ordered by their offset values, which represents
     * their position in the global ID space. This ordering is used to
     * ensure proper sequence of segments and detect expired segments.
     *
     * @param other The segment to compare to
     * @return A negative integer, zero, or a positive integer as this
     *         segment's offset is less than, equal to, or greater than
     *         the specified segment's offset
     */
    @Override
    default int compareTo(IdSegment other) {
        if (getOffset() == other.getOffset()) {
            return 0;
        }
        return getOffset() > other.getOffset() ? 1 : -1;
    }
    
    /**
     * Ensure that the next segment is valid relative to this segment.
     * 
     * <p>This method validates that the next segment has a higher offset
     * than this segment, preventing the use of expired or duplicate segments
     * that could cause ID conflicts.
     *
     * @param nextIdSegment The next segment to validate
     * @throws NextIdSegmentExpiredException if the next segment is invalid
     */
    default void ensureNextIdSegment(IdSegment nextIdSegment) throws NextIdSegmentExpiredException {
        if (compareTo(nextIdSegment) >= 0) {
            throw new NextIdSegmentExpiredException(this, nextIdSegment);
        }
    }
}
