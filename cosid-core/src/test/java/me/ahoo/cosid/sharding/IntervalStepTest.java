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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @author ahoo wang
 */
class IntervalStepTest {

    @Test
    void getUnit() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        Assertions.assertEquals(ChronoUnit.MONTHS, step.getUnit());
    }

    @Test
    void getAmount() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        Assertions.assertEquals(IntervalStep.DEFAULT_AMOUNT, step.getAmount());
    }

    @Test
    void next() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        LocalDateTime previous = LocalDateTime.of(2021, 12, 14, 22, 0);
        LocalDateTime expected = previous.plusMonths(1);
        LocalDateTime actualNext = step.next(previous);
        Assertions.assertEquals(expected, actualNext);
    }

    @Test
    void ofUnit() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        LocalDateTime dateTime = LocalDateTime.of(2021, 12, 14, 22, 0);
        LocalDateTime actual = step.ofUnit(dateTime);
        LocalDateTime expected = LocalDateTime.of(2021, 12, 1, 0, 0);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void unitOffset() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS);
        LocalDateTime start = LocalDateTime.of(2022, 10, 14, 22, 0);
        LocalDateTime time = LocalDateTime.of(2022, 12, 1, 0, 0);
        int actual = step.unitOffset(start, time);
        Assertions.assertEquals(2, actual);
    }


    @Test
    void unitOffsetWithStepOne() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS, 1);
        LocalDateTime startInterval = LocalDateTime.of(2021, 12, 1, 0, 0);
        int offset = step.unitOffset(startInterval, LocalDateTime.of(2021, 12, 25, 0, 0));
        Assertions.assertEquals(0, offset);

        offset = step.unitOffset(startInterval, LocalDateTime.of(2022, 1, 22, 0, 0));
        Assertions.assertEquals(1, offset);

        offset = step.unitOffset(startInterval, LocalDateTime.of(2022, 2, 1, 0, 0));
        Assertions.assertEquals(2, offset);

        offset = step.unitOffset(startInterval, LocalDateTime.of(2022, 12, 9, 0, 0));
        Assertions.assertEquals(12, offset);
    }

    @Test
    void unitOffsetWithStepTwo() {
        IntervalStep step = IntervalStep.of(ChronoUnit.MONTHS, 2);
        LocalDateTime low = LocalDateTime.of(2021, 12, 1, 0, 0);
        int offset = step.unitOffset(low, LocalDateTime.of(2021, 12, 25, 0, 0));
        Assertions.assertEquals(0, offset);

        offset = step.unitOffset(low, LocalDateTime.of(2022, 1, 22, 0, 0));
        Assertions.assertEquals(0, offset);

        offset = step.unitOffset(low, LocalDateTime.of(2022, 2, 1, 0, 0));
        Assertions.assertEquals(1, offset);

        offset = step.unitOffset(low, LocalDateTime.of(2022, 12, 9, 0, 0));
        Assertions.assertEquals(6, offset);
    }

    @Test
    void unitOffsetWithUnitDay() {
        LocalDateTime startInterval = LocalDateTime.of(2021, 12, 8, 22, 25);
        IntervalStep step = IntervalStep.of(ChronoUnit.DAYS, 1);
        LocalDateTime time = LocalDateTime.of(2021, 12, 10, 22, 24);
        int offset = step.unitOffset(startInterval, time);
        Assertions.assertEquals(2, offset);
        time = LocalDateTime.of(2021, 12, 10, 22, 25);
        offset = step.unitOffset(startInterval, time);
        Assertions.assertEquals(2, offset);
        time = LocalDateTime.of(2021, 12, 10, 22, 26);
        offset = step.unitOffset(startInterval, time);
        Assertions.assertEquals(2, offset);
        time = LocalDateTime.of(2021, 12, 9, 1, 26);
        offset = step.unitOffset(startInterval, time);
        Assertions.assertEquals(1, offset);
        startInterval = LocalDateTime.of(2021, 12, 28, 22, 25);
        time = LocalDateTime.of(2022, 1, 1, 22, 26);
        offset = step.unitOffset(startInterval, time);
        Assertions.assertEquals(4, offset);
    }
}
