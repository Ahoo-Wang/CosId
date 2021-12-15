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

package me.ahoo.cosid.sharding;

import com.google.common.collect.Range;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

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
        Assertions.assertNotNull(actual);
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
                arguments(Range.all(), ALL_NODES),
                /**
                 * Range.closed
                 */
                arguments(Range.closed(1L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3")),
                arguments(Range.closed(0L, 3L), ALL_NODES),
                arguments(Range.closed(0L, 4L), ALL_NODES),
                arguments(Range.closed(0L, 1L), new ExactCollection<>("t_mod_0", "t_mod_1")),
                arguments(Range.closed(3L, 4L), new ExactCollection<>("t_mod_0", "t_mod_3")),
                /**
                 * Range.closedOpen
                 */
                arguments(Range.closedOpen(1L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2")),
                arguments(Range.closedOpen(0L, 3L), new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2")),
                arguments(Range.closedOpen(0L, 4L), ALL_NODES),
                arguments(Range.closedOpen(0L, 1L), new ExactCollection<>("t_mod_0")),
                arguments(Range.closedOpen(3L, 4L), new ExactCollection<>("t_mod_3")),
                /**
                 * Range.openClosed
                 */
                arguments(Range.openClosed(1L, 3L), new ExactCollection<>("t_mod_2", "t_mod_3")),
                arguments(Range.openClosed(0L, 3L), new ExactCollection<>("t_mod_1", "t_mod_2", "t_mod_3")),
                arguments(Range.openClosed(0L, 4L), ALL_NODES),
                arguments(Range.openClosed(0L, 1L), new ExactCollection<>("t_mod_1")),
                arguments(Range.openClosed(3L, 4L), new ExactCollection<>("t_mod_0")),
                /**
                 * Range.open
                 */
                arguments(Range.open(1L, 3L), new ExactCollection<>( "t_mod_2")),
                arguments(Range.open(0L, 3L), new ExactCollection<>( "t_mod_1", "t_mod_2")),
                arguments(Range.open(0L, 4L), new ExactCollection<>( "t_mod_1", "t_mod_2", "t_mod_3")),
                arguments(Range.open(0L, 1L), ExactCollection.empty()),
                arguments(Range.open(3L, 4L), ExactCollection.empty()),
                /**
                 * Range.greaterThan
                 */
                arguments(Range.greaterThan(0L), ALL_NODES),
                arguments(Range.greaterThan(1L), ALL_NODES),
                arguments(Range.greaterThan(2L), ALL_NODES),
                arguments(Range.greaterThan(3L), ALL_NODES),
                arguments(Range.greaterThan(4L), ALL_NODES),
                arguments(Range.greaterThan(5L), ALL_NODES),
                /**
                 * Range.atLeast
                 */
                arguments(Range.atLeast(0L), ALL_NODES),
                arguments(Range.atLeast(1L), ALL_NODES),
                arguments(Range.atLeast(2L), ALL_NODES),
                arguments(Range.atLeast(3L), ALL_NODES),
                arguments(Range.atLeast(4L), ALL_NODES),
                arguments(Range.atLeast(5L), ALL_NODES),
                /**
                 * Range.lessThan
                 */
                arguments(Range.lessThan(0L), ExactCollection.empty()),
                arguments(Range.lessThan(1L), new ExactCollection<>( "t_mod_0")),
                arguments(Range.lessThan(2L), new ExactCollection<>( "t_mod_0", "t_mod_1")),
                arguments(Range.lessThan(3L), new ExactCollection<>( "t_mod_0", "t_mod_1", "t_mod_2")),
                arguments(Range.lessThan(4L), ALL_NODES),
                arguments(Range.lessThan(5L), ALL_NODES),
                /**
                 * Range.atMost
                 */
                arguments(Range.atMost(0L), new ExactCollection<>( "t_mod_0")),
                arguments(Range.atMost(1L), new ExactCollection<>( "t_mod_0", "t_mod_1")),
                arguments(Range.atMost(2L), new ExactCollection<>( "t_mod_0", "t_mod_1", "t_mod_2")),
                arguments(Range.atMost(3L), ALL_NODES),
                arguments(Range.atMost(4L), ALL_NODES),
                arguments(Range.atMost(5L), ALL_NODES)
        );
    }

    @ParameterizedTest
    @MethodSource("shardingRangeArgsProvider")
    public void shardingRange(Range<Long> shardingValue, Collection<String> expected) {
        ModCycle<Long> modCycle = createModCycle();
        Collection<String> actual = modCycle.sharding(shardingValue);
        Assertions.assertEquals(expected, actual);
    }

}
