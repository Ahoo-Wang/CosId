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

import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.stream.Stream;


/**
 * @author ahoo wang
 */
class ModCycleTest {

    public static final int DIVISOR = 4;
    public static final String LOGIC_NAME_PREFIX = "t_mod_";
    public static final ExactCollection<String> ALL_NODES = new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3");

    public static ModCycle<Long> createModCycle(int divisor) {
        return new ModCycle<>(divisor, LOGIC_NAME_PREFIX);
    }

    public static ModCycle<Long> createModCycle() {
        return createModCycle(DIVISOR);
    }


    @Test
    public void ctorWhenDivisorIsZero() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            createModCycle(0);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void getDivisor(int divisor) {
        ModCycle<Long> modCycle = createModCycle(divisor);
        Assertions.assertEquals(divisor, modCycle.getDivisor());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void getEffectiveNodes(int divisor) {
        ModCycle<Long> modCycle = createModCycle(divisor);
        Collection<String> actual = modCycle.getEffectiveNodes();
        Assertions.assertEquals(divisor, actual.size());
        ExactCollection<String> expected = new ExactCollection<>(divisor);
        for (int i = 0; i < divisor; i++) {
            expected.add(i, LOGIC_NAME_PREFIX + i);
        }
        Assertions.assertEquals(expected, modCycle.getEffectiveNodes());
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void shardingPrecise(long shardingValue) {
        ModCycle<Long> modCycle = createModCycle();
        String actual = modCycle.sharding(shardingValue);
        String expected = LOGIC_NAME_PREFIX + (shardingValue % DIVISOR);
        Assertions.assertEquals(expected, actual);
    }

    static Stream<Arguments> shardingRangeArgsProvider() {
        return Stream.of(
            arguments(ShardingRange.all(), ALL_NODES),
            /**
             * Range.closed
             */
            arguments(ShardingRange.closed(1L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3")),
            arguments(ShardingRange.closed(0L, 3L), ALL_NODES),
            arguments(ShardingRange.closed(0L, 4L), ALL_NODES),
            arguments(ShardingRange.closed(0L, 1L), new ExactCollection<>("t_mod_0", "t_mod_1")),
            arguments(ShardingRange.closed(3L, 4L), new ExactCollection<>("t_mod_0", "t_mod_3")),
            /**
             * Range.closedOpen
             */
            arguments(ShardingRange.closedOpen(1L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2")),
            arguments(ShardingRange.closedOpen(0L, 3L), new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2")),
            arguments(ShardingRange.closedOpen(0L, 4L), ALL_NODES),
            arguments(ShardingRange.closedOpen(0L, 1L), new ExactCollection<>("t_mod_0")),
            arguments(ShardingRange.closedOpen(3L, 4L), new ExactCollection<>("t_mod_3")),
            /**
             * Range.openClosed
             */
            arguments(ShardingRange.openClosed(1L, 3L), new ExactCollection<>("t_mod_2", "t_mod_3")),
            arguments(ShardingRange.openClosed(0L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3")),
            arguments(ShardingRange.openClosed(0L, 4L), ALL_NODES),
            arguments(ShardingRange.openClosed(0L, 1L), new ExactCollection<>("t_mod_1")),
            arguments(ShardingRange.openClosed(3L, 4L), new ExactCollection<>("t_mod_0")),
            /**
             * Range.open
             */
            arguments(ShardingRange.open(1L, 3L), new ExactCollection<>("t_mod_2")),
            arguments(ShardingRange.open(0L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2")),
            arguments(ShardingRange.open(0L, 4L), new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3")),
            arguments(ShardingRange.open(0L, 1L), ExactCollection.empty()),
            arguments(ShardingRange.open(3L, 4L), ExactCollection.empty()),
            /**
             * Range.greaterThan
             */
            arguments(ShardingRange.greaterThan(0L), ALL_NODES),
            arguments(ShardingRange.greaterThan(1L), ALL_NODES),
            arguments(ShardingRange.greaterThan(2L), ALL_NODES),
            arguments(ShardingRange.greaterThan(3L), ALL_NODES),
            arguments(ShardingRange.greaterThan(4L), ALL_NODES),
            arguments(ShardingRange.greaterThan(5L), ALL_NODES),
            /**
             * Range.atLeast
             */
            arguments(ShardingRange.atLeast(0L), ALL_NODES),
            arguments(ShardingRange.atLeast(1L), ALL_NODES),
            arguments(ShardingRange.atLeast(2L), ALL_NODES),
            arguments(ShardingRange.atLeast(3L), ALL_NODES),
            arguments(ShardingRange.atLeast(4L), ALL_NODES),
            arguments(ShardingRange.atLeast(5L), ALL_NODES),
            /**
             * Range.lessThan
             */
            arguments(ShardingRange.lessThan(0L), ExactCollection.empty()),
            arguments(ShardingRange.lessThan(1L), new ExactCollection<>("t_mod_0")),
            arguments(ShardingRange.lessThan(2L), new ExactCollection<>("t_mod_0", "t_mod_1")),
            arguments(ShardingRange.lessThan(3L), new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2")),
            arguments(ShardingRange.lessThan(4L), ALL_NODES),
            arguments(ShardingRange.lessThan(5L), ALL_NODES),
            /**
             * Range.atMost
             */
            arguments(ShardingRange.atMost(0L), new ExactCollection<>("t_mod_0")),
            arguments(ShardingRange.atMost(1L), new ExactCollection<>("t_mod_0", "t_mod_1")),
            arguments(ShardingRange.atMost(2L), new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2")),
            arguments(ShardingRange.atMost(3L), ALL_NODES),
            arguments(ShardingRange.atMost(4L), ALL_NODES),
            arguments(ShardingRange.atMost(5L), ALL_NODES),
            /**
             * Ranges whose span overflows int node-size arithmetic
             */
            arguments(ShardingRange.closed(0L, Long.MAX_VALUE), ALL_NODES),
            arguments(ShardingRange.atMost(Long.MAX_VALUE), ALL_NODES),
            arguments(ShardingRange.closedOpen(0L, Long.MAX_VALUE), ALL_NODES),
            arguments(ShardingRange.lessThan(Long.MAX_VALUE), ALL_NODES),
            arguments(ShardingRange.closedOpen(0L, 1L << 32), ALL_NODES),
            arguments(ShardingRange.closed(Long.MIN_VALUE, Long.MAX_VALUE), ALL_NODES),
            arguments(ShardingRange.closedOpen(Long.MIN_VALUE, Long.MAX_VALUE), ALL_NODES),
            /**
             * Sign-overflow guard branch combinations: cross-zero without wraparound, and negative-only spans
             */
            arguments(ShardingRange.closedOpen(-3L, 1L), ALL_NODES),
            arguments(ShardingRange.closed(-8L, -1L), ALL_NODES),
            /**
             * Empty ranges at equal endpoints
             */
            arguments(ShardingRange.closedOpen(3L, 3L), ExactCollection.empty()),
            arguments(ShardingRange.openClosed(3L, 3L), ExactCollection.empty())
        );
    }

    @ParameterizedTest
    @MethodSource("shardingRangeArgsProvider")
    public void shardingRange(ShardingRange<Long> shardingValue, Collection<String> expected) {
        ModCycle<Long> modCycle = createModCycle();
        Collection<String> actual = modCycle.sharding(shardingValue);
        Assertions.assertEquals(expected, actual);
    }


    @Test
    public void negativeValuesMapToANode() {
        ModCycle<Long> modCycle = createModCycle();
        Assertions.assertEquals("t_mod_3", modCycle.sharding(-1L));
        Assertions.assertEquals("t_mod_0", modCycle.sharding(-4L));
        Collection<String> nodes = modCycle.sharding(ShardingRange.closed(-2L, -1L));
        Assertions.assertEquals(new ExactCollection<>("t_mod_2", "t_mod_3"), nodes);
    }
}
