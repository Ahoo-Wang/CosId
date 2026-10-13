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

import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.segment.grouped.GroupedId;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


/**
 * Default segment algorithm ID generator.
 *
 * <p>Provides thread-safe ID generation using a segment-based approach.
 * Allocates ID segments from a central distributor and generates IDs
 * locally within each segment for high throughput.
 *
 * @author ahoo wang
 */
@Slf4j
public class DefaultSegmentId implements SegmentId {

    private final long idSegmentTtl;
    private final IdSegmentDistributor maxIdDistributor;

    @GuardedBy("this")
    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;

    /**
     * Creates a generator with infinite segment TTL.
     *
     * @param maxIdDistributor the segment distributor
     */
    public DefaultSegmentId(IdSegmentDistributor maxIdDistributor) {
        this(TIME_TO_LIVE_FOREVER, maxIdDistributor);
    }

    /**
     * Creates a generator with specified segment TTL.
     *
     * @param idSegmentTtl segment time-to-live in seconds
     * @param maxIdDistributor the segment distributor
     */
    public DefaultSegmentId(long idSegmentTtl, IdSegmentDistributor maxIdDistributor) {
        Checks.checkArgument(idSegmentTtl > 0, "idSegmentTtl:[%s] must be greater than 0.", idSegmentTtl);

        this.idSegmentTtl = idSegmentTtl;
        this.maxIdDistributor = maxIdDistributor;
    }

    @Override
    public IdSegment current() {
        return segment;
    }

    @Override
    public long generate() {
        return next(null);
    }
    
    @Override
    public @NonNull GroupedId generateGrouped() {
        IdSegment[] source = new IdSegment[1];
        long id = next(source);
        return GroupedId.of(source[0].group(), id);
    }
    
    /**
     * Generate the next id.
     *
     * @param source when not {@code null}, receives the segment the id came from
     */
    private long next(IdSegment @Nullable [] source) {

        if (maxIdDistributor.getStep() == ONE_STEP) {
            // The segment binds the group its id came from: reading the distributor's group separately could see the next
            // group after a rollover and label an id of the old counter with it.
            IdSegment oneStep = SegmentFetchEvents.timed(maxIdDistributor.getNamespacedName(), () -> maxIdDistributor.nextIdSegment(idSegmentTtl));
            // Expired already: fetched across the end of its group.
            while (oneStep.isExpired()) {
                oneStep = SegmentFetchEvents.timed(maxIdDistributor.getNamespacedName(), () -> maxIdDistributor.nextIdSegment(idSegmentTtl));
            }
            return sourced(source, oneStep, oneStep.getMaxId());
        }
        long nextSeq;
        if (segment.isAvailable()) {
            IdSegment current = segment;
            nextSeq = current.incrementAndGet();
            if (!current.isOverflow(nextSeq)) {
                return sourced(source, current, nextSeq);
            }
        }

        synchronized (this) {
            while (true) {
                if (segment.isAvailable()) {
                    nextSeq = segment.incrementAndGet();
                    if (!segment.isOverflow(nextSeq)) {
                        return sourced(source, segment, nextSeq);
                    }
                }
                IdSegment nextIdSegment = SegmentFetchEvents.timed(maxIdDistributor.getNamespacedName(), () -> maxIdDistributor.nextIdSegment(idSegmentTtl));
                if (!maxIdDistributor.allowReset()) {
                    segment.ensureNextIdSegment(nextIdSegment);
                }
                segment = nextIdSegment;
            }
        }
    }
    
    private static long sourced(IdSegment @Nullable [] source, IdSegment segment, long id) {
        if (source != null) {
            source[0] = segment;
        }
        return id;
    }
}
