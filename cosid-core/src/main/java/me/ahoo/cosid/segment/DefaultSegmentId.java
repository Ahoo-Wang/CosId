/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosid.segment;

import lombok.extern.slf4j.Slf4j;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultSegmentId implements SegmentId {

    private final IdSegmentDistributor maxIdDistributor;

    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;

    public DefaultSegmentId(IdSegmentDistributor maxIdDistributor) {
        this.maxIdDistributor = maxIdDistributor;
    }

    @Override
    public long generate() {

        if (maxIdDistributor.getStep() == ONE_STEP) {
            return maxIdDistributor.nextMaxId();
        }

        long nextSeq = segment.incrementAndGet();
        if (!segment.isOverflow(nextSeq)){
            return nextSeq;
        }

        synchronized (this) {
            while (true) {
                nextSeq = segment.incrementAndGet();
                if(!segment.isOverflow(nextSeq)){
                    return nextSeq;
                }
                IdSegment nextIdSegment = maxIdDistributor.nextIdSegment();
                segment.ensureNextIdSegment(nextIdSegment);
                segment = nextIdSegment;
            }
        }
    }

}
