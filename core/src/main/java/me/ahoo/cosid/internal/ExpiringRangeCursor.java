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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.time.TimeSource;

/**
 * A cursor with a deadline, for {@code BatchPolicy.rangeTtl} (04-segment.md §2): past it the rest of the range is
 * dropped. A separate class, so generators without a time to live pay nothing for it.
 */
public final class ExpiringRangeCursor extends RangeCursor {
    private final long expiresAtMillis;
    private final TimeSource time;

    /**
     * A cursor on {@code [start, end)} until {@code expiresAtMillis}.
     *
     * @param start           the first value
     * @param end             the end, exclusive, already capped
     * @param expiresAtMillis from this time on nothing is issued
     * @param time            the time source
     */
    public ExpiringRangeCursor(long start, long end, long expiresAtMillis, TimeSource time) {
        super(start, end);
        this.expiresAtMillis = expiresAtMillis;
        this.time = time;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    @Override
    public long take() {
        if (time.currentMillis() >= expiresAtMillis) {
            return -1L;
        }
        return super.take();
    }

    @Override
    public long remaining(long nowMillis) {
        return nowMillis >= expiresAtMillis ? 0L : super.remaining(nowMillis);
    }
}
