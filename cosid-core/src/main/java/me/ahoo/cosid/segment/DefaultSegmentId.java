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

import me.ahoo.cosid.segment.grouped.GroupedAccessor;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;



/**
 * Default segment algorithm ID generator.
 *
 * @author ahoo wang
 */
@Slf4j
public class DefaultSegmentId implements SegmentId {
    
    private final long idSegmentTtl;
    private final IdSegmentDistributor maxIdDistributor;
    
    @GuardedBy("this")
    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;
    
    public DefaultSegmentId(IdSegmentDistributor maxIdDistributor) {
        this(TIME_TO_LIVE_FOREVER, maxIdDistributor);
    }
    
    public DefaultSegmentId(long idSegmentTtl, IdSegmentDistributor maxIdDistributor) {
        Preconditions.checkArgument(idSegmentTtl > 0, "idSegmentTtl:[%s] must be greater than 0.", idSegmentTtl);
        
        this.idSegmentTtl = idSegmentTtl;
        this.maxIdDistributor = maxIdDistributor;
    }
    
    @Override
    public IdSegment current() {
        return segment;
    }
    
    @Override
    public long generate() {
        
        if (maxIdDistributor.getStep() == ONE_STEP) {
            GroupedAccessor.setIfNotNever(maxIdDistributor.group());
            return maxIdDistributor.nextMaxId();
        }
        long nextSeq;
        if (segment.isAvailable()) {
            nextSeq = segment.incrementAndGet();
            if (!segment.isOverflow(nextSeq)) {
                return nextSeq;
            }
        }
        
        synchronized (this) {
            while (true) {
                if (segment.isAvailable()) {
                    nextSeq = segment.incrementAndGet();
                    if (!segment.isOverflow(nextSeq)) {
                        return nextSeq;
                    }
                }
                IdSegment nextIdSegment = maxIdDistributor.nextIdSegment(idSegmentTtl);
                if (!maxIdDistributor.allowReset()) {
                    segment.ensureNextIdSegment(nextIdSegment);
                }
                segment = nextIdSegment;
            }
        }
    }
    
}
