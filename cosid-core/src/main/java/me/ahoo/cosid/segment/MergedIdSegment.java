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

import me.ahoo.cosid.segment.grouped.GroupedKey;

import java.util.concurrent.TimeUnit;

/**
 * Merged ID segment that divides a segment into multiple sub-segments.
 *
 * <p>This allows a single allocated segment to be shared among multiple
 * consumers or purposes by splitting the step into equal portions.
 *
 * @author ahoo wang
 */
public class MergedIdSegment implements IdSegment {

    private final int segments;
    private final IdSegment idSegment;
    private final long singleStep;

    /**
     * Creates a merged segment.
     *
     * @param segments  number of sub-segments to create
     * @param idSegment the underlying segment
     */
    public MergedIdSegment(int segments, IdSegment idSegment) {
        this.segments = segments;
        this.idSegment = idSegment;
        this.singleStep = idSegment.getStep() / segments;
    }

    @Override
    public GroupedKey group() {
        return idSegment.group();
    }

    /**
     * Gets the number of sub-segments.
     *
     * @return number of segments
     */
    public int getSegments() {
        return segments;
    }

    /**
     * Gets the step size for each sub-segment.
     *
     * @return single step size
     */
    public long getSingleStep() {
        return singleStep;
    }

    /**
     * ID segment fetch time.
     * unit {@link TimeUnit#MILLISECONDS}
     *
     * @return Fetch Time
     */
    @Override
    public long getFetchTime() {
        return idSegment.getFetchTime();
    }

    @Override
    public long getMaxId() {
        return idSegment.getMaxId();
    }

    @Override
    public long getOffset() {
        return idSegment.getOffset();
    }

    @Override
    public long getSequence() {
        return idSegment.getSequence();
    }

    @Override
    public long getStep() {
        return idSegment.getStep();
    }

    @Override
    public long getTtl() {
        return idSegment.getTtl();
    }

    @Override
    public long incrementAndGet() {
        return idSegment.incrementAndGet();
    }

    @Override
    public String toString() {
        return "MergedIdSegment{"
            + "segments=" + segments
            + ", idSegment=" + idSegment
            + ", singleStep=" + singleStep
            + '}';
    }
}
