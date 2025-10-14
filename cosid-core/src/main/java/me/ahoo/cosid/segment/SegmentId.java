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

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;
import me.ahoo.cosid.stat.generator.SegmentIdStat;

/**
 * Segment algorithm ID generator.
 * 
 * <p>This interface implements a segment-based ID generation algorithm that
 * works by allocating contiguous blocks (segments) of IDs from a central
 * authority. Each segment contains:
 * 
 * <ul>
 *   <li>A range of IDs (offset to max ID)</li>
 *   <li>A sequence counter to track usage within the segment</li>
 *   <li>Metadata for expiration and availability</li>
 * </ul>
 * 
 * <p>The segment algorithm works as follows:
 * <ol>
 *   <li>Request a segment (block of IDs) from a central distributor</li>
 *   <li>Generate IDs locally within that segment</li>
 *   <li>When the segment is exhausted, request a new segment</li>
 * </ol>
 * 
 * <p>This approach provides:
 * <ul>
 *   <li>High throughput (local generation within segments)</li>
 *   <li>Global uniqueness (central coordination for segments)</li>
 *   <li>Resilience (continues working if distributor is temporarily unavailable)</li>
 * </ul>
 * 
 * <p><img src="../doc-files/SegmentId.png" alt="SegmentId"></p>
 *
 * @author ahoo wang
 */
public interface SegmentId extends IdGenerator {
    /**
     * The default step size for segment allocation (1 ID at a time).
     * 
     * <p>This constant represents the minimum allocation unit for IDs,
     * typically used when requesting individual IDs rather than segments.
     */
    int ONE_STEP = 1;

    /**
     * Get the current ID segment being used for generation.
     * 
     * <p>This method returns the segment from which IDs are currently being
     * allocated. The segment contains the range of available IDs and tracks
     * the current position within that range.
     *
     * @return The current ID segment
     */
    IdSegment current();

    /**
     * Get statistical information about this Segment ID generator.
     * 
     * <p>This method provides detailed information about the generator's
     * current state, including segment metadata and availability status.
     *
     * @return Statistical information about this Segment ID generator
     */
    @Override
    default IdGeneratorStat stat() {
        return new SegmentIdStat(getClass().getSimpleName(),
                current().getFetchTime(),
                current().getMaxId(),
                current().getOffset(),
                current().getSequence(),
                current().getStep(),
                current().isExpired(),
                current().isOverflow(),
                current().isAvailable(),
                current().group(),
                idConverter().stat()
        );
    }
}
