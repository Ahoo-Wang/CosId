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

package me.ahoo.cosid.shardingsphere.sharding.interval;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.ExactCollection;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * @author ahoo wang
 */
class SmartIntervalShardingAlgorithmTest extends AbstractIntervalShardingAlgorithmTest {

    AbstractIntervalShardingAlgorithm shardingAlgorithm;

    @BeforeEach
    void init() {
        shardingAlgorithm = new SmartIntervalShardingAlgorithm();
        shardingAlgorithm.setProps(getProps());
        shardingAlgorithm.init();
    }

    static Stream<Arguments> doShardingPreciseWhenLocalDateTimeArgsProvider() {
        return Stream.of(
                arguments(LOWER_DATE_TIME, "table_202101"),
                arguments(LocalDateTime.of(2021, 2, 14, 22, 0), "table_202102"),
                arguments(LocalDateTime.of(2021, 10, 1, 0, 0), "table_202110"),
                arguments(UPPER_DATE_TIME, "table_202201")
        );
    }

    @ParameterizedTest
    @MethodSource("doShardingPreciseWhenLocalDateTimeArgsProvider")
    public void doShardingPreciseWhenLocalDateTime(LocalDateTime dateTime, String expected) {
        PreciseShardingValue shardingValue = new PreciseShardingValue<>(LOGIC_NAME, COLUMN_NAME, dateTime);
        String actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }

    static Stream<Arguments> doShardingPreciseWhenStringArgsProvider() {
        return Stream.of(
                arguments("2021-02-14 22:00:00", "table_202102"),
                arguments("2021-10-01 00:00:00", "table_202110")
        );
    }

    @ParameterizedTest
    @MethodSource("doShardingPreciseWhenStringArgsProvider")
    public void doShardingPreciseWhenString(String dateTime, String expected) {
        PreciseShardingValue shardingValue = new PreciseShardingValue<>(LOGIC_NAME, COLUMN_NAME, dateTime);
        String actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }


    static Stream<Arguments> doShardingPreciseWhenDateArgsProvider() {
        return Stream.of(
                arguments(new Date(LocalDateTime.of(2021, 2, 14, 22, 0).toInstant(ZONE_OFFSET_SHANGHAI).toEpochMilli()), "table_202102"),
                arguments(new Date(LocalDateTime.of(2021, 10, 1, 0, 0).toInstant(ZONE_OFFSET_SHANGHAI).toEpochMilli()), "table_202110")
        );
    }

    @ParameterizedTest
    @MethodSource("doShardingPreciseWhenDateArgsProvider")
    public void doShardingPreciseWhenDate(Date dateTime, String expected) {
        PreciseShardingValue shardingValue = new PreciseShardingValue<>(LOGIC_NAME, COLUMN_NAME, dateTime);
        String actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }

    static Stream<Arguments> doShardingPreciseWhenTimestampArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 2, 14, 22, 0).toInstant(ZONE_OFFSET_SHANGHAI).toEpochMilli(), "table_202102"),
                arguments(LocalDateTime.of(2021, 10, 1, 22, 0).toInstant(ZONE_OFFSET_SHANGHAI).toEpochMilli(), "table_202110")
        );
    }

    @ParameterizedTest
    @MethodSource("doShardingPreciseWhenTimestampArgsProvider")
    public void doShardingPreciseWhenTimestamp(long dateTime, String expected) {
        PreciseShardingValue shardingValue = new PreciseShardingValue<>(LOGIC_NAME, COLUMN_NAME, dateTime);
        String actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }


    static Stream<Arguments> doShardingRangeArgsProvider() {
        return Stream.of(
                arguments(Range.all(), ALL_NODES),
                arguments(Range.closed(LOWER_DATE_TIME, UPPER_DATE_TIME), ALL_NODES),
                arguments(Range.closed(LocalDateTime.of(2021, 1, 1, 0, 0), LocalDateTime.of(2021, 2, 1, 0, 0)), new ExactCollection<>("table_202101", "table_202102")),
                arguments(Range.closed(LOWER_DATE_TIME.minusMonths(1), UPPER_DATE_TIME.plusMonths(1)), ALL_NODES),
                arguments(Range.closed(LocalDateTime.of(2021, 12, 1, 0, 0), LocalDateTime.of(2022, 2, 1, 0, 0)), new ExactCollection<>("table_202112", "table_202201")),
                arguments(Range.closedOpen(LOWER_DATE_TIME, UPPER_DATE_TIME), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105", "table_202106", "table_202107", "table_202108", "table_202109", "table_202110", "table_202111", "table_202112")),
                arguments(Range.openClosed(LOWER_DATE_TIME, UPPER_DATE_TIME), ALL_NODES),

                arguments(Range.greaterThan(LOWER_DATE_TIME), ALL_NODES),
                arguments(Range.atLeast(LOWER_DATE_TIME), ALL_NODES),
                arguments(Range.greaterThan(UPPER_DATE_TIME), new ExactCollection<>("table_202201")),
                arguments(Range.atLeast(UPPER_DATE_TIME), new ExactCollection<>("table_202201")),
                arguments(Range.greaterThan(LocalDateTime.of(2021, 12, 5, 0, 0)), new ExactCollection<>("table_202112", "table_202201")),
                arguments(Range.atLeast(LocalDateTime.of(2021, 12, 5, 0, 0)), new ExactCollection<>("table_202112", "table_202201")),

                arguments(Range.lessThan(LOWER_DATE_TIME), ExactCollection.empty()),
                arguments(Range.atMost(LOWER_DATE_TIME), new ExactCollection<>("table_202101")),
                arguments(Range.lessThan(UPPER_DATE_TIME), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105", "table_202106", "table_202107", "table_202108", "table_202109", "table_202110", "table_202111", "table_202112")),
                arguments(Range.atMost(UPPER_DATE_TIME), ALL_NODES),
                arguments(Range.lessThan(LocalDateTime.of(2021, 5, 5, 0, 0)), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105")),
                arguments(Range.atMost(LocalDateTime.of(2021, 5, 5, 0, 0)), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105"))
        );
    }

    @ParameterizedTest
    @MethodSource("doShardingRangeArgsProvider")
    public void doShardingRange(Range<LocalDateTime> rangeValue, Collection<String> expected) {
        RangeShardingValue<LocalDateTime> shardingValue = new RangeShardingValue<>(LOGIC_NAME, COLUMN_NAME, rangeValue);
        Collection<String> actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }

    static Stream<Arguments> doShardingRangeWhenStringArgsProvider() {
        return doShardingRangeArgsProvider().map(arguments -> {
            Range shardingValue = (Range) arguments.get()[0];
            if (Range.all().equals(shardingValue)) {
                return arguments;
            }
            if (shardingValue.hasLowerBound() && shardingValue.hasUpperBound()) {
                String lower = shardingValue.lowerEndpoint().toString().replace("T", " ") + ":00";
                String upper = shardingValue.upperEndpoint().toString().replace("T", " ") + ":00";
                arguments.get()[0] = Range.range(lower, shardingValue.lowerBoundType(), upper, shardingValue.upperBoundType());
                return arguments;
            }

            if (shardingValue.hasLowerBound()) {
                String lower = shardingValue.lowerEndpoint().toString().replace("T", " ") + ":00";
                if (BoundType.OPEN.equals(shardingValue.lowerBoundType())) {
                    arguments.get()[0] = Range.greaterThan(lower);
                }
                arguments.get()[0] = Range.atLeast(lower);
                return arguments;
            }

            String upper = shardingValue.upperEndpoint().toString().replace("T", " ") + ":00";
            if (BoundType.OPEN.equals(shardingValue.upperBoundType())) {
                arguments.get()[0] = Range.lessThan(upper);
                return arguments;
            }
            arguments.get()[0] = Range.atMost(upper);
            return arguments;

        });
    }

    @ParameterizedTest
    @MethodSource("doShardingRangeWhenStringArgsProvider")
    public void doShardingRangeWhenString(Range<String> rangeValue, Collection<String> expected) {
        RangeShardingValue<String> shardingValue = new RangeShardingValue<>(LOGIC_NAME, COLUMN_NAME, rangeValue);
        Collection<String> actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }
}
