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

package me.ahoo.cosid.sharding;

import java.util.Objects;

/**
 * A range of sharding values, the query a {@link RangeSharding} answers. It replaces the Guava {@code Range} of 4.x, so
 * that {@code cosid-sharding} depends on {@code cosid-core} alone (D7); the factory methods keep Guava's names.
 *
 * <p>A missing endpoint ({@code null}) means the range is unbounded on that side; its bound type is then
 * {@link BoundType#OPEN}. A range is never inverted: the lower bound is at most the upper bound, and {@code (a, a)}
 * is rejected, as in Guava. {@code [a, a)} and {@code (a, a]} are the empty ranges.
 *
 * @param lower     the lower endpoint, {@code null} for none
 * @param lowerType whether the lower endpoint belongs to the range
 * @param upper     the upper endpoint, {@code null} for none
 * @param upperType whether the upper endpoint belongs to the range
 * @param <C>       the type of the values
 */
public record ShardingRange<C extends Comparable<? super C>>(C lower, BoundType lowerType, C upper, BoundType upperType) {
    private static final ShardingRange<?> ALL = new ShardingRange<>(null, BoundType.OPEN, null, BoundType.OPEN);

    /**
     * Checks the bounds.
     */
    public ShardingRange {
        lowerType = lower == null ? BoundType.OPEN : Objects.requireNonNull(lowerType, "lowerType");
        upperType = upper == null ? BoundType.OPEN : Objects.requireNonNull(upperType, "upperType");
        if (lower != null && upper != null) {
            int order = lower.compareTo(upper);
            if (order > 0 || order == 0 && lowerType == BoundType.OPEN && upperType == BoundType.OPEN) {
                throw new IllegalArgumentException("Invalid range: " + describe(lower, lowerType, upper, upperType));
            }
        }
    }

    /**
     * Every value.
     *
     * @param <C> the type of the values
     * @return {@code (-∞..+∞)}
     */
    @SuppressWarnings("unchecked")
    public static <C extends Comparable<? super C>> ShardingRange<C> all() {
        return (ShardingRange<C>) ALL;
    }

    /**
     * {@code [lower..upper]}.
     *
     * @param lower the lower endpoint, included
     * @param upper the upper endpoint, included
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> closed(C lower, C upper) {
        return new ShardingRange<>(Objects.requireNonNull(lower), BoundType.CLOSED, Objects.requireNonNull(upper), BoundType.CLOSED);
    }

    /**
     * {@code (lower..upper)}.
     *
     * @param lower the lower endpoint, excluded
     * @param upper the upper endpoint, excluded
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> open(C lower, C upper) {
        return new ShardingRange<>(Objects.requireNonNull(lower), BoundType.OPEN, Objects.requireNonNull(upper), BoundType.OPEN);
    }

    /**
     * {@code [lower..upper)}.
     *
     * @param lower the lower endpoint, included
     * @param upper the upper endpoint, excluded
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> closedOpen(C lower, C upper) {
        return new ShardingRange<>(Objects.requireNonNull(lower), BoundType.CLOSED, Objects.requireNonNull(upper), BoundType.OPEN);
    }

    /**
     * {@code (lower..upper]}.
     *
     * @param lower the lower endpoint, excluded
     * @param upper the upper endpoint, included
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> openClosed(C lower, C upper) {
        return new ShardingRange<>(Objects.requireNonNull(lower), BoundType.OPEN, Objects.requireNonNull(upper), BoundType.CLOSED);
    }

    /**
     * {@code [lower..+∞)}.
     *
     * @param lower the lower endpoint, included
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> atLeast(C lower) {
        return new ShardingRange<>(Objects.requireNonNull(lower), BoundType.CLOSED, null, BoundType.OPEN);
    }

    /**
     * {@code (lower..+∞)}.
     *
     * @param lower the lower endpoint, excluded
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> greaterThan(C lower) {
        return new ShardingRange<>(Objects.requireNonNull(lower), BoundType.OPEN, null, BoundType.OPEN);
    }

    /**
     * {@code (-∞..upper]}.
     *
     * @param upper the upper endpoint, included
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> atMost(C upper) {
        return new ShardingRange<>(null, BoundType.OPEN, Objects.requireNonNull(upper), BoundType.CLOSED);
    }

    /**
     * {@code (-∞..upper)}.
     *
     * @param upper the upper endpoint, excluded
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> lessThan(C upper) {
        return new ShardingRange<>(null, BoundType.OPEN, Objects.requireNonNull(upper), BoundType.OPEN);
    }

    /**
     * {@code [value..value]}.
     *
     * @param value the only value
     * @param <C>   the type of the values
     * @return the range
     */
    public static <C extends Comparable<? super C>> ShardingRange<C> singleton(C value) {
        return closed(value, value);
    }

    /**
     * Whether the range has a lower endpoint.
     *
     * @return true when bounded below
     */
    public boolean hasLowerBound() {
        return lower != null;
    }

    /**
     * Whether the range has an upper endpoint.
     *
     * @return true when bounded above
     */
    public boolean hasUpperBound() {
        return upper != null;
    }

    /**
     * The lower endpoint.
     *
     * @return the lower endpoint
     * @throws IllegalStateException when the range is unbounded below
     */
    public C lowerEndpoint() {
        if (lower == null) {
            throw new IllegalStateException("Range unbounded on this side: " + this);
        }
        return lower;
    }

    /**
     * The upper endpoint.
     *
     * @return the upper endpoint
     * @throws IllegalStateException when the range is unbounded above
     */
    public C upperEndpoint() {
        if (upper == null) {
            throw new IllegalStateException("Range unbounded on this side: " + this);
        }
        return upper;
    }

    /**
     * Whether the lower endpoint belongs to the range.
     *
     * @return the lower bound type
     * @throws IllegalStateException when the range is unbounded below
     */
    public BoundType lowerBoundType() {
        lowerEndpoint();
        return lowerType;
    }

    /**
     * Whether the upper endpoint belongs to the range.
     *
     * @return the upper bound type
     * @throws IllegalStateException when the range is unbounded above
     */
    public BoundType upperBoundType() {
        upperEndpoint();
        return upperType;
    }

    /**
     * Whether the range holds no value: {@code [a..a)} or {@code (a..a]}.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return lower != null && upper != null && lower.compareTo(upper) == 0 && lowerType != upperType;
    }

    /**
     * Whether the value lies in the range.
     *
     * @param value the value
     * @return true when the range contains it
     */
    public boolean contains(C value) {
        Objects.requireNonNull(value, "value");
        if (lower != null) {
            int order = lower.compareTo(value);
            if (order > 0 || order == 0 && lowerType == BoundType.OPEN) {
                return false;
            }
        }
        if (upper != null) {
            int order = value.compareTo(upper);
            return order < 0 || order == 0 && upperType == BoundType.CLOSED;
        }
        return true;
    }

    /**
     * Whether some range, possibly empty, is enclosed by both ranges, as Guava's {@code Range.isConnected}:
     * {@code [1..2)} and {@code [2..3]} are connected, {@code [1..2)} and {@code (2..3]} are not.
     *
     * @param other the other range
     * @return true when connected
     */
    public boolean isConnected(ShardingRange<C> other) {
        return lowerBelowUpper(lower, lowerType, other.upper, other.upperType)
            && lowerBelowUpper(other.lower, other.lowerType, upper, upperType);
    }

    /**
     * Whether a lower bound is at most an upper bound, as Guava's cuts: a closed lower bound lies just below its
     * endpoint, an open one just above; a closed upper bound lies just above, an open one just below.
     */
    private static <C extends Comparable<? super C>> boolean lowerBelowUpper(C lower, BoundType lowerType, C upper, BoundType upperType) {
        if (lower == null || upper == null) {
            return true;
        }
        int order = lower.compareTo(upper);
        return order < 0 || order == 0 && (lowerType == BoundType.CLOSED || upperType == BoundType.CLOSED);
    }

    @Override
    public String toString() {
        return describe(lower, lowerType, upper, upperType);
    }

    private static String describe(Object lower, BoundType lowerType, Object upper, BoundType upperType) {
        return (lower == null ? "(-∞" : (lowerType == BoundType.CLOSED ? "[" : "(") + lower)
            + ".."
            + (upper == null ? "+∞)" : upper + (upperType == BoundType.CLOSED ? "]" : ")"));
    }
}
