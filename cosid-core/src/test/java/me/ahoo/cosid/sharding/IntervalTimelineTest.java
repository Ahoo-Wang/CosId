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

import com.google.common.collect.Range;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.stream.Stream;


/**
 * @author ahoo wang
 */
class IntervalTimelineTest {

    public static final LocalDateTime LOWER_DATE_TIME = IntervalStepTest.START_DATE_TIME;
    public static final LocalDateTime UPPER_DATE_TIME = IntervalStepTest.START_DATE_TIME.plusYears(1);
    public static final String LOGIC_NAME = "table";
    public static final DateTimeFormatter SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("_yyyyMM");
    public static final ExactCollection<String> ALL_NODES = new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105",
        "table_202106", "table_202107", "table_202108", "table_202109", "table_202110", "table_202111", "table_202112", "table_202201");
    private IntervalTimeline intervalTimeline;

    @BeforeEach
    void init() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        intervalTimeline = new IntervalTimeline(LOGIC_NAME, Range.closed(LOWER_DATE_TIME, UPPER_DATE_TIME), step, SUFFIX_FORMATTER);
    }

    public static Stream<Arguments> shardingArgsProvider() {
        return Stream.of(
            arguments(LOWER_DATE_TIME, "table_202101"),
            arguments(LocalDateTime.of(2021, 2, 14, 22, 0), "table_202102"),
            arguments(LocalDateTime.of(2021, 10, 1, 0, 0), "table_202110"),
            arguments(UPPER_DATE_TIME, "table_202201")
        );
    }

    @ParameterizedTest
    @MethodSource("shardingArgsProvider")
    void sharding(LocalDateTime dateTime, String expected) {
        String actual = intervalTimeline.sharding(dateTime);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void shardingOutOfBound() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            intervalTimeline.sharding(LocalDateTime.MIN);
        });
    }

    public static Stream<Arguments> shardingRangeArgsProvider() {
        return Stream.of(
            arguments(Range.all(), ALL_NODES),
            arguments(Range.closed(LOWER_DATE_TIME, UPPER_DATE_TIME), ALL_NODES),
            arguments(Range.closed(LocalDateTime.of(2021, 1, 1, 0, 0), LocalDateTime.of(2021, 2, 1, 0, 0)), new ExactCollection<>("table_202101", "table_202102")),
            arguments(Range.closed(LOWER_DATE_TIME.minusMonths(1), UPPER_DATE_TIME.plusMonths(1)), ALL_NODES),
            arguments(Range.closed(LocalDateTime.of(2021, 12, 1, 0, 0), LocalDateTime.of(2022, 2, 1, 0, 0)), new ExactCollection<>("table_202112", "table_202201")),
            arguments(Range.closedOpen(LOWER_DATE_TIME, UPPER_DATE_TIME),
                new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105", "table_202106", "table_202107", "table_202108", "table_202109", "table_202110",
                    "table_202111", "table_202112")),
            arguments(Range.openClosed(LOWER_DATE_TIME, UPPER_DATE_TIME), ALL_NODES),

            arguments(Range.greaterThan(LOWER_DATE_TIME), ALL_NODES),
            arguments(Range.atLeast(LOWER_DATE_TIME), ALL_NODES),
            arguments(Range.greaterThan(UPPER_DATE_TIME), new ExactCollection<>("table_202201")),
            arguments(Range.atLeast(UPPER_DATE_TIME), new ExactCollection<>("table_202201")),
            arguments(Range.greaterThan(LocalDateTime.of(2021, 12, 5, 0, 0)), new ExactCollection<>("table_202112", "table_202201")),
            arguments(Range.atLeast(LocalDateTime.of(2021, 12, 5, 0, 0)), new ExactCollection<>("table_202112", "table_202201")),

            arguments(Range.lessThan(LOWER_DATE_TIME), ExactCollection.empty()),
            arguments(Range.atMost(LOWER_DATE_TIME), new ExactCollection<>("table_202101")),
            arguments(Range.lessThan(UPPER_DATE_TIME),
                new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105", "table_202106", "table_202107", "table_202108", "table_202109", "table_202110",
                    "table_202111", "table_202112")),
            arguments(Range.atMost(UPPER_DATE_TIME), ALL_NODES),
            arguments(Range.lessThan(LocalDateTime.of(2021, 5, 5, 0, 0)), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105")),
            arguments(Range.atMost(LocalDateTime.of(2021, 5, 5, 0, 0)), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105"))
        );
    }

    @ParameterizedTest
    @MethodSource("shardingRangeArgsProvider")
    void shardingRange(Range<LocalDateTime> shardingValue, Collection<String> expected) {
        Collection<String> actual = intervalTimeline.sharding(shardingValue);
        Assertions.assertEquals(expected, actual);
    }
}
