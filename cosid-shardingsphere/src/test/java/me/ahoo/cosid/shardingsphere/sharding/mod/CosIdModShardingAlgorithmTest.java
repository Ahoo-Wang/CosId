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

package me.ahoo.cosid.shardingsphere.sharding.mod;

import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.ExactCollection;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * @author ahoo wang
 */
class CosIdModShardingAlgorithmTest {

    public static final int DIVISOR = 4;
    public static final String LOGIC_NAME = "t_mod";
    public static final String COLUMN_NAME = "id";
    public static final String LOGIC_NAME_PREFIX = LOGIC_NAME + "_";
    public static final ExactCollection<String> ALL_NODES = new ExactCollection<>("t_mod_0", "t_mod_1", "t_mod_2", "t_mod_3");

    private CosIdModShardingAlgorithm shardingAlgorithm;

    @BeforeEach
    public void setup() {
        Properties properties = new Properties();
        properties.setProperty(CosIdAlgorithm.LOGIC_NAME_PREFIX_KEY, LOGIC_NAME_PREFIX);
        properties.setProperty(CosIdModShardingAlgorithm.MODULO_KEY, String.valueOf(DIVISOR));
        shardingAlgorithm = new CosIdModShardingAlgorithm();
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void doShardingPrecise(long value) {
        PreciseShardingValue shardingValue = new PreciseShardingValue<>(LOGIC_NAME, COLUMN_NAME, value);
        String actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        String expected = LOGIC_NAME_PREFIX + (value % DIVISOR);
        assertEquals(expected, actual);
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
    public void doShardingRange(Range<Long> rangeValue, Collection<String> expected) {
        RangeShardingValue<Long> shardingValue = new RangeShardingValue<>(LOGIC_NAME, COLUMN_NAME, rangeValue);
        Collection<String> actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }
}
