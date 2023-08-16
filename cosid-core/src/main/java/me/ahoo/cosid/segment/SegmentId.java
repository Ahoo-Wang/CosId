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
 * <p><img src="../doc-files/SegmentId.png" alt="SegmentId"></p>
 *
 * @author ahoo wang
 */
public interface SegmentId extends IdGenerator {
    int ONE_STEP = 1;

    IdSegment current();

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
                idConverter().stat()
        );
    }
}
