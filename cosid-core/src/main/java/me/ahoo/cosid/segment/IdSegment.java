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

import me.ahoo.cosid.util.Clock;

import javax.annotation.concurrent.ThreadSafe;


/**
 * Id Segment.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdSegment extends Comparable<IdSegment> {

    long SEQUENCE_OVERFLOW = -1;

    long TIME_TO_LIVE_FOREVER = Long.MAX_VALUE;

    /**
     * ID segment fetch time.
     * unit {@link java.util.concurrent.TimeUnit#SECONDS}
     *
     * @return fetch time
     */
    long getFetchTime();

    long getMaxId();

    long getOffset();

    long getSequence();

    long getStep();

    /**
     * the id segment time to live.
     * unit {@link java.util.concurrent.TimeUnit#SECONDS}
     *
     * @return time to live
     */
    default long getTtl() {
        return TIME_TO_LIVE_FOREVER;
    }

    /**
     * id segment has expired?.
     *
     * @return expired?
     */
    default boolean isExpired() {
        if (TIME_TO_LIVE_FOREVER == getTtl()) {
            /*
             * Very important! Avoid getting the current clock,
             * because getting the clock is too slow.
             */
            return false;
        }
        return Clock.CACHE.secondTime() - getFetchTime() > getTtl();
    }

    default boolean isOverflow() {
        return getSequence() >= getMaxId();
    }

    default boolean isOverflow(long nextSeq) {
        return nextSeq == SEQUENCE_OVERFLOW || nextSeq > getMaxId();
    }

    /**
     * not expired and not overflow.
     *
     * @return true when not expired and not overflow
     */
    default boolean isAvailable() {
        return !isExpired() && !isOverflow();
    }

    long incrementAndGet();

    @Override
    default int compareTo(IdSegment other) {
        if (getOffset() == other.getOffset()) {
            return 0;
        }
        return getOffset() > other.getOffset() ? 1 : -1;
    }

    default void ensureNextIdSegment(IdSegment nextIdSegment) throws NextIdSegmentExpiredException {
        if (compareTo(nextIdSegment) >= 0) {
            throw new NextIdSegmentExpiredException(this, nextIdSegment);
        }
    }
}
