/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

/**
 * @author ahoo wang
 */
public interface IdSegment extends Comparable<IdSegment> {

    long SEQUENCE_OVERFLOW = -1;

    long TIME_TO_LIVE_FOREVER = Long.MAX_VALUE;

    /**
     * ID segment fetch time
     * unit {@link java.util.concurrent.TimeUnit#SECONDS}
     *
     * @return
     */
    long getFetchTime();

    long getMaxId();

    long getOffset();

    long getSequence();

    long getStep();

    /**
     * the id segment time to live
     * unit {@link java.util.concurrent.TimeUnit#SECONDS}
     *
     * @return the id segment time to live
     */
    default long getTtl() {
        return TIME_TO_LIVE_FOREVER;
    }

    /**
     * id segment has expired?
     *
     * @return
     */
    default boolean isExpired() {
        if (TIME_TO_LIVE_FOREVER == getTtl()) {
            /**
             * Very important! Avoid getting the current clock, because getting the clock is too slow.
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
     * not expired and not overflow
     *
     * @return
     */
    default boolean isAvailable() {
        return !isExpired() && !isOverflow();
    }

    long incrementAndGet();


    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * <p>The implementor must ensure
     * {@code sgn(x.compareTo(y)) == -sgn(y.compareTo(x))}
     * for all {@code x} and {@code y}.  (This
     * implies that {@code x.compareTo(y)} must throw an exception iff
     * {@code y.compareTo(x)} throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies
     * {@code x.compareTo(z) > 0}.
     *
     * <p>Finally, the implementor must ensure that {@code x.compareTo(y)==0}
     * implies that {@code sgn(x.compareTo(z)) == sgn(y.compareTo(z))}, for
     * all {@code z}.
     *
     * <p>It is strongly recommended, but <i>not</i> strictly required that
     * {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any
     * class that implements the {@code Comparable} interface and violates
     * this condition should clearly indicate this fact.  The recommended
     * language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     *
     * <p>In the foregoing description, the notation
     * {@code sgn(}<i>expression</i>{@code )} designates the mathematical
     * <i>signum</i> function, which is defined to return one of {@code -1},
     * {@code 0}, or {@code 1} according to whether the value of
     * <i>expression</i> is negative, zero, or positive, respectively.
     *
     * @param other the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     */
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
