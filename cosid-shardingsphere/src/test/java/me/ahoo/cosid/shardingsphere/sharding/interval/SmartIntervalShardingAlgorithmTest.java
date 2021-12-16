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

import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.ExactCollection;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * @author ahoo wang
 */
class SmartIntervalShardingAlgorithmTest extends AbstractIntervalShardingAlgorithmTest {


    private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(StringIntervalShardingAlgorithm.DEFAULT_DATE_TIME_PATTERN);

    AbstractIntervalShardingAlgorithm shardingAlgorithm;

    @BeforeEach
    void init() {
        shardingAlgorithm = new SmartIntervalShardingAlgorithm();
        Properties properties = getProps();
        properties.setProperty(StringIntervalShardingAlgorithm.DATE_TIME_PATTERN_KEY, StringIntervalShardingAlgorithm.DEFAULT_DATE_TIME_PATTERN);
        shardingAlgorithm.setProps(properties);
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


    static Stream<Arguments> doShardingRangeArgsProvider(Function<LocalDateTime, ? extends Comparable<?>> datetimeConvert) {
        return Stream.of(
                arguments(Range.all(), ALL_NODES),
                arguments(Range.closed(datetimeConvert.apply(LOWER_DATE_TIME), datetimeConvert.apply(UPPER_DATE_TIME)), ALL_NODES),
                arguments(Range.closed(datetimeConvert.apply(LocalDateTime.of(2021, 1, 1, 0, 0)), datetimeConvert.apply(LocalDateTime.of(2021, 2, 1, 0, 0))), new ExactCollection<>("table_202101", "table_202102")),
                arguments(Range.closed(datetimeConvert.apply(LOWER_DATE_TIME.minusMonths(1)), datetimeConvert.apply(UPPER_DATE_TIME.plusMonths(1))), ALL_NODES),
                arguments(Range.closed(datetimeConvert.apply(LocalDateTime.of(2021, 12, 1, 0, 0)), datetimeConvert.apply(LocalDateTime.of(2022, 2, 1, 0, 0))), new ExactCollection<>("table_202112", "table_202201")),
                arguments(Range.closedOpen(datetimeConvert.apply(LOWER_DATE_TIME), datetimeConvert.apply(UPPER_DATE_TIME)), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105", "table_202106", "table_202107", "table_202108", "table_202109", "table_202110", "table_202111", "table_202112")),
                arguments(Range.openClosed(datetimeConvert.apply(LOWER_DATE_TIME), datetimeConvert.apply(UPPER_DATE_TIME)), ALL_NODES),

                arguments(Range.greaterThan(datetimeConvert.apply(LOWER_DATE_TIME)), ALL_NODES),
                arguments(Range.atLeast(datetimeConvert.apply(LOWER_DATE_TIME)), ALL_NODES),
                arguments(Range.greaterThan(datetimeConvert.apply(UPPER_DATE_TIME)), new ExactCollection<>("table_202201")),
                arguments(Range.atLeast(datetimeConvert.apply(UPPER_DATE_TIME)), new ExactCollection<>("table_202201")),
                arguments(Range.greaterThan(datetimeConvert.apply(LocalDateTime.of(2021, 12, 5, 0, 0))), new ExactCollection<>("table_202112", "table_202201")),
                arguments(Range.atLeast(datetimeConvert.apply(LocalDateTime.of(2021, 12, 5, 0, 0))), new ExactCollection<>("table_202112", "table_202201")),

                arguments(Range.lessThan(datetimeConvert.apply(LOWER_DATE_TIME)), ExactCollection.empty()),
                arguments(Range.atMost(datetimeConvert.apply(LOWER_DATE_TIME)), new ExactCollection<>("table_202101")),
                arguments(Range.lessThan(datetimeConvert.apply(UPPER_DATE_TIME)), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105", "table_202106", "table_202107", "table_202108", "table_202109", "table_202110", "table_202111", "table_202112")),
                arguments(Range.atMost(datetimeConvert.apply(UPPER_DATE_TIME)), ALL_NODES),
                arguments(Range.lessThan(datetimeConvert.apply(LocalDateTime.of(2021, 5, 5, 0, 0))), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105")),
                arguments(Range.atMost(datetimeConvert.apply(LocalDateTime.of(2021, 5, 5, 0, 0))), new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105"))
        );
    }


    static Stream<Arguments> doShardingRangeArgsProviderAsLocalDateTime() {
        return doShardingRangeArgsProvider((ldt -> ldt));
    }

    static Stream<Arguments> doShardingRangeArgsProviderAsString() {
        return doShardingRangeArgsProvider((ldt -> ldt.format(dateTimeFormatter)));
    }

    @ParameterizedTest
    @MethodSource("doShardingRangeArgsProviderAsLocalDateTime")
    public void doShardingRange(Range<LocalDateTime> rangeValue, Collection<String> expected) {
        RangeShardingValue<LocalDateTime> shardingValue = new RangeShardingValue<>(LOGIC_NAME, COLUMN_NAME, rangeValue);
        Collection<String> actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }


    @ParameterizedTest
    @MethodSource("doShardingRangeArgsProviderAsString")
    public void doShardingRangeWhenString(Range<String> rangeValue, Collection<String> expected) {
        RangeShardingValue<String> shardingValue = new RangeShardingValue<>(LOGIC_NAME, COLUMN_NAME, rangeValue);
        Collection<String> actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }
}
