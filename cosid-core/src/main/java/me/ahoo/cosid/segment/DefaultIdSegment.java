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

import com.google.common.base.Preconditions;
import me.ahoo.cosid.util.Clock;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author ahoo wang
 */
public class DefaultIdSegment implements IdSegment {

    public static final DefaultIdSegment OVERFLOW = new DefaultIdSegment(IdSegment.SEQUENCE_OVERFLOW, 0, Clock.CACHE.secondTime(), TIME_TO_LIVE_FOREVER);

    /**
     * include
     */
    private final long maxId;
    private final long offset;
    private final long step;
    private volatile long sequence;
    private final long fetchTime;
    private final long ttl;

    private static final AtomicLongFieldUpdater<DefaultIdSegment> S = AtomicLongFieldUpdater.newUpdater(DefaultIdSegment.class, "sequence");

    public DefaultIdSegment(long maxId, long step) {
        this(maxId, step, Clock.CACHE.secondTime(), TIME_TO_LIVE_FOREVER);
    }

    public DefaultIdSegment(long maxId, long step, long fetchTime, long ttl) {
        Preconditions.checkArgument(ttl > 0, "ttl:[%s] must be greater than 0.", ttl);
        this.maxId = maxId;
        this.step = step;
        this.offset = maxId - step;
        this.sequence = offset;
        this.fetchTime = fetchTime;
        this.ttl = ttl;
    }

    @Override
    public long getFetchTime() {
        return fetchTime;
    }

    @Override
    public long getTtl() {
        return ttl;
    }

    @Override
    public long getMaxId() {
        return maxId;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public long getSequence() {
        return sequence;
    }

    @Override
    public long getStep() {
        return step;
    }

    @Override
    public long incrementAndGet() {
        if (isOverflow()) {
            return SEQUENCE_OVERFLOW;
        }

        final long nextSeq = S.incrementAndGet(this);

        if (isOverflow(nextSeq)) {
            return SEQUENCE_OVERFLOW;
        }
        return nextSeq;
    }

    @Override
    public String toString() {
        return "DefaultIdSegment{" +
                "maxId=" + maxId +
                ", offset=" + offset +
                ", step=" + step +
                ", sequence=" + sequence +
                ", fetchTime=" + fetchTime +
                ", ttl=" + ttl +
                '}';
    }
}
