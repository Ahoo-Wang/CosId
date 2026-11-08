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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Modulo-based cyclical sharding algorithm.
 *
 * <p>Distributes IDs across nodes using modulo arithmetic.
 * Maps a sharding value to a node by computing: {@code value % divisor}.
 *
 * <p><img src="../doc-files/CosIdModShardingAlgorithm.png" alt="CosIdModShardingAlgorithm"></p>
 *
 * @param <T> the type of number that is comparable
 * @author ahoo wang
 */
public class ModCycle<T extends Number & Comparable<T>> implements Sharding<T> {
    private final int divisor;
    private final String logicNamePrefix;
    private final ExactCollection<String> effectiveNodes;

    /**
     * Creates a ModCycle sharding algorithm.
     *
     * @param divisor the number of nodes to distribute across
     * @param logicNamePrefix the prefix for node names
     */
    public ModCycle(int divisor, String logicNamePrefix) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("divisor must be greater than 0!");
        }
        this.divisor = divisor;
        this.logicNamePrefix = logicNamePrefix;
        this.effectiveNodes = initNodes(divisor, logicNamePrefix);
    }

    private static ExactCollection<String> initNodes(int divisor, String logicNamePrefix) {
        ExactCollection<String> modNodes = new ExactCollection<>(divisor);

        for (int i = 0; i < divisor; i++) {
            String nodeName = logicNamePrefix + i;
            modNodes.add(i, nodeName);
        }

        return modNodes;
    }

    /**
     * Gets the divisor.
     *
     * @return the divisor
     */
    public int getDivisor() {
        return divisor;
    }

    @Override
    public String sharding(T shardingValue) {
        int nodeIdx = (int) Math.floorMod(shardingValue.longValue(), (long) divisor);
        return effectiveNodes.get(nodeIdx);
    }

    /**
     * The nodes of every value in a range: the nodes {@link #sharding(Number)} routes the range's values to, or a few
     * more, never fewer.
     *
     * <p>A range is taken in the domain of {@link Number#longValue()}, which routes a value: an integral type
     * ({@code Long}, {@code Integer}, {@code Short}, {@code Byte}, {@code BigInteger}) by its exact values, so an open
     * bound excludes its endpoint; a missing bound reaches to the end of the domain ({@code Long.MIN_VALUE} or
     * {@code Long.MAX_VALUE}, a {@code BigInteger} without end), negative values included. A fractional type
     * ({@code BigDecimal}, {@code Double}, {@code Float}) routes by its integer part, which the values beside an open
     * endpoint share with it, so its bounds are taken as closed. A {@code BigInteger} or {@code BigDecimal} beyond a long
     * routes by the low 64 bits of its integer part, as {@code longValue()} does.
     *
     * @param shardingValue the range
     * @return the nodes of the range's values
     */
    @Override
    public Collection<String> sharding(ShardingRange<T> shardingValue) {
        if (ShardingRange.all().equals(shardingValue)) {
            return effectiveNodes;
        }
        if (shardingValue.isEmpty()) {
            return ExactCollection.empty();
        }
        T endpoint = shardingValue.hasLowerBound() ? shardingValue.lowerEndpoint() : shardingValue.upperEndpoint();
        if (endpoint instanceof BigInteger || endpoint instanceof BigDecimal) {
            return bigSharding(shardingValue);
        }
        boolean exact = endpoint instanceof Long || endpoint instanceof Integer || endpoint instanceof Short || endpoint instanceof Byte;
        long lower = Long.MIN_VALUE;
        if (shardingValue.hasLowerBound()) {
            lower = shardingValue.lowerEndpoint().longValue();
            if (exact && BoundType.OPEN.equals(shardingValue.lowerBoundType())) {
                if (lower == Long.MAX_VALUE) {
                    return ExactCollection.empty();
                }
                lower++;
            }
        }
        long upper = Long.MAX_VALUE;
        if (shardingValue.hasUpperBound()) {
            upper = shardingValue.upperEndpoint().longValue();
            if (exact && BoundType.OPEN.equals(shardingValue.upperBoundType())) {
                if (upper == Long.MIN_VALUE) {
                    return ExactCollection.empty();
                }
                upper--;
            }
        }
        if (upper < lower) {
            return ExactCollection.empty();
        }
        // upper - lower + 1 values, the difference read unsigned (it reaches 2^64 - 1): divisor of them cover every node.
        long span = upper - lower;
        if (Long.compareUnsigned(span, divisor - 1L) >= 0) {
            return effectiveNodes;
        }
        // Counted, not compared to upper: lower + i never passes upper, so it never overflows past Long.MAX_VALUE.
        int nodeSize = (int) span + 1;
        ExactCollection<String> nodes = new ExactCollection<>(nodeSize);
        for (int i = 0; i < nodeSize; i++) {
            nodes.add(i, effectiveNodes.get((int) Math.floorMod(lower + i, (long) divisor)));
        }
        return nodes;
    }

    private Collection<String> bigSharding(ShardingRange<T> shardingValue) {
        if (!shardingValue.hasLowerBound() || !shardingValue.hasUpperBound()) {
            return effectiveNodes;
        }
        BigInteger lower = integral(shardingValue.lowerEndpoint(), shardingValue.lowerBoundType(), BigInteger.ONE);
        BigInteger upper = integral(shardingValue.upperEndpoint(), shardingValue.upperBoundType(), BigInteger.ONE.negate());
        BigInteger span = upper.subtract(lower);
        if (span.signum() < 0) {
            return ExactCollection.empty();
        }
        if (span.compareTo(BigInteger.valueOf(divisor - 1L)) >= 0) {
            return effectiveNodes;
        }
        // Fewer values than nodes; their low 64 bits wrap at most once, where two of them may share a node.
        Set<String> nodes = new LinkedHashSet<>();
        for (BigInteger value = lower; value.compareTo(upper) <= 0; value = value.add(BigInteger.ONE)) {
            nodes.add(effectiveNodes.get((int) Math.floorMod(value.longValue(), (long) divisor)));
        }
        return new ExactCollection<>(nodes.toArray());
    }

    /**
     * The first (step 1) or last (step -1) integer a bound reaches: a {@code BigInteger} endpoint, past it when the bound
     * is open; the integer part of a {@code BigDecimal} endpoint, which the values beside it share, open or closed.
     */
    private static BigInteger integral(Number endpoint, BoundType boundType, BigInteger step) {
        if (endpoint instanceof BigInteger integer) {
            return BoundType.OPEN.equals(boundType) ? integer.add(step) : integer;
        }
        return ((BigDecimal) endpoint).toBigInteger();
    }

    @Override
    public Collection<String> getEffectiveNodes() {
        return effectiveNodes;
    }
}
