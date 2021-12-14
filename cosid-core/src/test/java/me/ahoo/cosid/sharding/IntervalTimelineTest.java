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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * @author ahoo wang
 */
class IntervalTimelineTest {

    /**
     * 2021-12-08 22:25 ~ 2023-01-01 00:00
     *
     * @param chronoUnit
     * @param amount
     * @return
     */
    public static IntervalTimeline createLine(ChronoUnit chronoUnit, int amount) {
        LocalDateTime lower = LocalDateTime.of(2021, 12, 8, 22, 25);
        LocalDateTime upper = LocalDateTime.of(2023, 1, 1, 0, 0);
        IntervalTimeline.Step step = IntervalTimeline.Step.of(chronoUnit, amount);
        String logicName = "table";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("_yyyyMM");

        return new IntervalTimeline(logicName, Range.closed(lower, upper), step, formatter);
    }

    @Test
    void size_step_1() {
        IntervalTimeline intervalTimeline = createLine(ChronoUnit.MONTHS, 1);
        Assertions.assertNotNull(intervalTimeline);
        Assertions.assertEquals(14, intervalTimeline.size());
        Assertions.assertEquals(LocalDateTime.of(2021, 12, 1, 0, 0), intervalTimeline.getStartInterval().getLower());
    }

    @Test
    void size_step_2() {
        IntervalTimeline intervalTimeline = createLine(ChronoUnit.MONTHS, 2);
        Assertions.assertNotNull(intervalTimeline);
        Assertions.assertEquals(7, intervalTimeline.size());
    }

    @Test
    void size_step_3() {
        IntervalTimeline intervalTimeline = createLine(ChronoUnit.MONTHS, 3);
        Assertions.assertNotNull(intervalTimeline);
        Assertions.assertEquals(5, intervalTimeline.size());
    }

    @Test
    void size_step_day_1() {
        LocalDateTime startInterval = LocalDateTime.of(2021, 12, 8, 22, 25);
        IntervalTimeline.Step step = IntervalTimeline.Step.of(ChronoUnit.DAYS, 1);
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

    @Test
    void step_unitOffset() {
        IntervalTimeline.Step step = IntervalTimeline.Step.of(ChronoUnit.MONTHS, 1);
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
    void step_unitOffset_2() {
        IntervalTimeline.Step step = IntervalTimeline.Step.of(ChronoUnit.MONTHS, 2);
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
    void get() {
        IntervalTimeline intervalTimeline = createLine(ChronoUnit.MONTHS, 1);
        String node = intervalTimeline.sharding(LocalDateTime.of(2021, 12, 8, 22, 25));
        Assertions.assertEquals("table_202112", node);
        node = intervalTimeline.sharding(LocalDateTime.of(2022, 1, 1, 0, 0));
        Assertions.assertEquals("table_202201", node);
        node = intervalTimeline.sharding(LocalDateTime.of(2023, 1, 1, 0, 0));
        Assertions.assertEquals("table_202301", node);
    }


    @Test
    void getRange() {
        IntervalTimeline intervalTimeline = createLine(ChronoUnit.MONTHS, 1);

        LocalDateTime shardingLower = LocalDateTime.of(2021, 12, 8, 22, 25);
        LocalDateTime shardingUpper = LocalDateTime.of(2022, 5, 1, 0, 0);
        Range<LocalDateTime> closedRange = Range.closed(shardingLower, shardingUpper);
        Collection<String> nodes = intervalTimeline.sharding(closedRange);
        Assertions.assertEquals(new ExactCollection<>("table_202112", "table_202201", "table_202202", "table_202203", "table_202204", "table_202205"), nodes);

        Range<LocalDateTime> closedOpenRange = Range.closedOpen(shardingLower, shardingUpper);
        nodes = intervalTimeline.sharding(closedOpenRange);
        Assertions.assertEquals(new ExactCollection<>("table_202112", "table_202201", "table_202202", "table_202203", "table_202204"), nodes);

        Range<LocalDateTime> openClosedRange = Range.openClosed(shardingLower, shardingUpper);
        nodes = intervalTimeline.sharding(openClosedRange);
        Assertions.assertEquals(new ExactCollection<>("table_202112", "table_202201", "table_202202", "table_202203", "table_202204", "table_202205"), nodes);

        Range<LocalDateTime> openRange = Range.open(shardingLower, shardingUpper);
        nodes = intervalTimeline.sharding(openRange);
        Assertions.assertEquals(new ExactCollection<>("table_202112", "table_202201", "table_202202", "table_202203", "table_202204"), nodes);
    }

    @Test
    void getRange_critical() {
        /**
         * 2021-12-08 22:25 ~ 2023-01-01 00:00
         */
        IntervalTimeline intervalTimeline = createLine(ChronoUnit.MONTHS, 1);

        LocalDateTime shardingLower = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTime shardingUpper = LocalDateTime.of(2022, 3, 1, 0, 0);
        Collection<String> nodes = intervalTimeline.sharding(Range.open(shardingLower, shardingUpper));
        Assertions.assertEquals(new ExactCollection<>("table_202201", "table_202202"), nodes);

        nodes = intervalTimeline.sharding(Range.singleton(shardingLower));
        Assertions.assertEquals(new ExactCollection<>("table_202201"), nodes);

        shardingUpper = LocalDateTime.of(2022, 2, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.lessThan(shardingUpper));
        Assertions.assertEquals(new ExactCollection<>("table_202112", "table_202201"), nodes);

        nodes = intervalTimeline.sharding(Range.atMost(shardingUpper));
        Assertions.assertEquals(new ExactCollection<>("table_202112", "table_202201", "table_202202"), nodes);

        shardingUpper = LocalDateTime.of(2024, 1, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.atMost(shardingUpper));
        Assertions.assertEquals(intervalTimeline.size(), nodes.size());

        shardingUpper = LocalDateTime.of(2020, 1, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.atLeast(shardingUpper));
        Assertions.assertEquals(intervalTimeline.size(), nodes.size());

        shardingLower = LocalDateTime.of(2022, 1, 1, 0, 0);
        shardingUpper = LocalDateTime.of(2022, 2, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.open(shardingLower, shardingUpper));
        Assertions.assertEquals(new ExactCollection<>("table_202201"), nodes);

        shardingLower = LocalDateTime.of(2021, 12, 8, 22, 20);
        shardingUpper = LocalDateTime.of(2022, 2, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.closed(shardingLower, shardingUpper));
        Assertions.assertEquals(3, nodes.size());

        shardingLower = LocalDateTime.of(2021, 12, 8, 22, 20);
        shardingUpper = LocalDateTime.of(2024, 1, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.closed(shardingLower, shardingUpper));
        Assertions.assertEquals(intervalTimeline.size(), nodes.size());

        shardingLower = LocalDateTime.of(2020, 12, 8, 22, 20);
        shardingUpper = LocalDateTime.of(2024, 1, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.closed(shardingLower, shardingUpper));
        Assertions.assertEquals(intervalTimeline.size(), nodes.size());

        shardingLower = LocalDateTime.of(2000, 1, 1, 0, 0);
        shardingUpper = LocalDateTime.of(2002, 2, 1, 0, 0);
        nodes = intervalTimeline.sharding(Range.open(shardingLower, shardingUpper));
        Assertions.assertEquals(0, nodes.size());
    }
}
