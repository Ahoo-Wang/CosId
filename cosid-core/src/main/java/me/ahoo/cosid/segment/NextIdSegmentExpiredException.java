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

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Strings;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Exception thrown when a next ID segment is invalid.
 *
 * <p>This exception indicates that the provided next segment has an offset
 * that is not greater than the current segment's offset, which would cause
 * ID conflicts or duplication.
 *
 * @author ahoo wang
 */
public class NextIdSegmentExpiredException extends CosIdException {
    private static final AtomicLong times = new AtomicLong(0);
    private final IdSegment current;
    private final IdSegment next;

    /**
     * Creates a new exception.
     *
     * @param current the current segment
     * @param next    the invalid next segment
     */
    public NextIdSegmentExpiredException(IdSegment current, IdSegment next) {
        super(Strings.lenientFormat("The next IdSegment:[%s] cannot be before the current IdSegment:[%s]-- times:[%s].",
            next,
            current,
            times.incrementAndGet())
        );
        this.current = current;
        this.next = next;
    }

    /**
     * Gets the current segment.
     *
     * @return the current segment
     */
    public IdSegment getCurrent() {
        return current;
    }

    /**
     * Gets the invalid next segment.
     *
     * @return the next segment
     */
    public IdSegment getNext() {
        return next;
    }
}
