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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * The range a segment generator issues from (04-segment.md §2.1): one atomic increment and one comparison per value.
 *
 * <p>The counter keeps counting past {@code end}: a value at or past the end is never issued. A cursor is only ever
 * incremented by the threads that read it while it was the generator's current cursor, so it passes its end by at most
 * the number of such threads; {@link #of} keeps every end {@link #HEADROOM} below {@code Long.MAX_VALUE} so the
 * counter never wraps around into issuable values.
 */
public class RangeCursor {
    /** Values within this distance of {@code Long.MAX_VALUE} are never issued (a gap, which D19 allows). */
    public static final long HEADROOM = 1L << 32;
    private static final VarHandle NEXT;

    static {
        try {
            NEXT = MethodHandles.lookup().findVarHandle(RangeCursor.class, "next", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final long end;
    private volatile long next;

    /**
     * A cursor on {@code [next, end)}, unchecked: also the empty and closed sentinels.
     *
     * @param next the first value
     * @param end  the end, exclusive
     */
    public RangeCursor(long next, long end) {
        this.next = next;
        this.end = end;
    }

    /**
     * A cursor on {@code [start, end)}, its end capped {@link #HEADROOM} below {@code Long.MAX_VALUE}.
     *
     * @param start the first value
     * @param end   the end, exclusive
     * @return the cursor, or null if nothing of the range is left after the cap
     */
    public static RangeCursor of(long start, long end) {
        long capped = cap(end);
        return start < capped ? new RangeCursor(start, capped) : null;
    }

    /**
     * {@code end} capped {@link #HEADROOM} below {@code Long.MAX_VALUE}.
     *
     * @param end the end of a range
     * @return the end a cursor may issue up to
     */
    public static long cap(long end) {
        return Math.min(end, Long.MAX_VALUE - HEADROOM);
    }

    /**
     * Takes the next value.
     *
     * @return the value, or {@code -1} if the cursor is used up (or expired)
     */
    public long take() {
        long value = (long) NEXT.getAndAdd(this, 1L);
        return value < end ? value : -1L;
    }

    /**
     * The values not taken yet, ignoring expiry.
     *
     * @return {@code end - next}, at least 0
     */
    public long untaken() {
        long taken = (long) NEXT.getVolatile(this);
        return taken < end ? end - taken : 0L;
    }

    public long end() {
        return end;
    }

    /**
     * The values not taken yet.
     *
     * @param nowMillis the current time
     * @return the values left, 0 if none or expired
     */
    public long remaining(long nowMillis) {
        long taken = (long) NEXT.getVolatile(this);
        return taken < end ? end - taken : 0L;
    }

    /**
     * Whether a value is left at {@code nowMillis}.
     *
     * @param nowMillis the current time
     * @return true if {@link #take()} may still return a value
     */
    public boolean usable(long nowMillis) {
        return remaining(nowMillis) > 0;
    }
}
