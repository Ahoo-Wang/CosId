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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;


/**
 * @author ahoo wang
 */
class IntervalStepTest {

    public static final LocalDateTime START_DATE_TIME = LocalDateTime.of(2021, 1, 1, 0, 0);

    @ParameterizedTest
    @EnumSource(ChronoUnit.class)
    void getUnit(ChronoUnit unit) {
        IntervalStep step = IntervalStep.of(unit);
        Assertions.assertEquals(unit, step.getUnit());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void getAmount(int amount) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS, amount);
        Assertions.assertEquals(amount, step.getAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void next(int amount) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS, amount);
        LocalDateTime expected = START_DATE_TIME.plusMonths(amount);
        LocalDateTime actual = step.next(START_DATE_TIME);
        Assertions.assertEquals(expected, actual);
    }

    static Stream<Arguments> floorUnitYearArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), LocalDateTime.of(2021, 1, 1, 0, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), LocalDateTime.of(2022, 1, 1, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitYearArgsProvider")
    void floorUnitYear(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.YEARS);
        LocalDateTime actual = step.floorUnit(dateTime);
        Assertions.assertEquals(expected, actual);
    }

    static Stream<Arguments> floorUnitMonthArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), LocalDateTime.of(2021, 12, 1, 0, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), LocalDateTime.of(2022, 11, 1, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitMonthArgsProvider")
    void floorUnitMonth(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        LocalDateTime actual = step.floorUnit(dateTime);
        Assertions.assertEquals(expected, actual);
    }

    static Stream<Arguments> floorUnitDayArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), LocalDateTime.of(2021, 12, 14, 0, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), LocalDateTime.of(2022, 11, 14, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitDayArgsProvider")
    void floorUnitDay(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.DAYS);
        LocalDateTime actual = step.floorUnit(dateTime);
        Assertions.assertEquals(expected, actual);
    }


    static Stream<Arguments> floorUnitHourArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), LocalDateTime.of(2021, 12, 14, 22, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), LocalDateTime.of(2022, 11, 14, 22, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitHourArgsProvider")
    void floorUnitHour(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.HOURS);
        LocalDateTime actual = step.floorUnit(dateTime);
        Assertions.assertEquals(expected, actual);
    }


    static Stream<Arguments> floorUnitMinutesArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0, 0), LocalDateTime.of(2021, 12, 14, 22, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 0, 1), LocalDateTime.of(2022, 11, 14, 22, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitMinutesArgsProvider")
    void floorUnitMinutes(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MINUTES);
        LocalDateTime actual = step.floorUnit(dateTime);
        Assertions.assertEquals(expected, actual);
    }


    static Stream<Arguments> floorUnitSecondArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0, 0, 0), LocalDateTime.of(2021, 12, 14, 22, 0, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 0, 0, 1), LocalDateTime.of(2022, 11, 14, 22, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitSecondArgsProvider")
    void floorUnitSeconds(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MINUTES);
        LocalDateTime actual = step.floorUnit(dateTime);
        Assertions.assertEquals(expected, actual);
    }

    static Stream<Arguments> floorUnitDefaultArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0, 0, 0), LocalDateTime.of(2021, 12, 14, 22, 0, 0)),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 0, 0, 1), LocalDateTime.of(2022, 11, 14, 22, 0, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("floorUnitDefaultArgsProvider")
    void floorUnitDefaults(LocalDateTime dateTime, LocalDateTime expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.NANOS);
        Assertions.assertThrows(IllegalStateException.class, () -> {
            step.floorUnit(dateTime);
        });
    }

    static Stream<Arguments> offsetUnitArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 1, 28, 22, 1), 0),
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), 11),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), 22)
        );
    }

    @ParameterizedTest
    @MethodSource("offsetUnitArgsProvider")
    void offsetUnit(LocalDateTime dateTime, int expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        int actual = step.offsetUnit(START_DATE_TIME, dateTime);
        Assertions.assertEquals(expected, actual);
    }

    static Stream<Arguments> offsetUnitAmountTwoArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 1, 28, 22, 1), 0),
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), 5),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), 11)
        );
    }

    @ParameterizedTest
    @MethodSource("offsetUnitAmountTwoArgsProvider")
    void offsetUnitAmountTwo(LocalDateTime dateTime, int expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS, 2);
        int actual = step.offsetUnit(START_DATE_TIME, dateTime);
        Assertions.assertEquals(expected, actual);
    }
    
    static Stream<Arguments> offsetUnitDayArgsProvider() {
        return Stream.of(
                arguments(LocalDateTime.of(2021, 1, 28, 22, 1), 27),
                arguments(LocalDateTime.of(2021, 12, 14, 22, 0), 347),
                arguments(LocalDateTime.of(2022, 11, 14, 22, 1), 682)
        );
    }

    @ParameterizedTest
    @MethodSource("offsetUnitDayArgsProvider")
    void offsetUnitDay(LocalDateTime dateTime, int expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.DAYS);
        int actual = step.offsetUnit(START_DATE_TIME, dateTime);
        Assertions.assertEquals(expected, actual);
    }
    
    static Stream<Arguments> offsetUnitHourArgsProvider() {
        return Stream.of(
            arguments(LocalDateTime.of(2021, 1, 1, 22, 1), 22),
            arguments(LocalDateTime.of(2021, 1, 2, 1, 0), 25)
        );
    }
    
    @ParameterizedTest
    @MethodSource("offsetUnitHourArgsProvider")
    void offsetUnitHour(LocalDateTime dateTime, int expected) {
        IntervalStep step = IntervalStep.of(ChronoUnit.HOURS);
        int actual = step.offsetUnit(START_DATE_TIME, dateTime);
        Assertions.assertEquals(expected, actual);
    }
}
