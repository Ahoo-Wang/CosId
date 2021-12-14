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

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class IntervalTimeline implements Sharding<LocalDateTime> {

    private final Range<LocalDateTime> effectiveInterval;
    private final Step step;
    private final Interval startInterval;
    private final Interval[] effectiveIntervals;
    private final String logicName;
    private final DateTimeFormatter suffixFormatter;
    private final ExactCollection<String> effectiveNodes;

    public IntervalTimeline(String logicName, Range<LocalDateTime> effectiveInterval, Step step, DateTimeFormatter suffixFormatter) {
        this.effectiveInterval = effectiveInterval;
        this.step = step;
        this.logicName = logicName;
        this.suffixFormatter = suffixFormatter;
        this.effectiveIntervals = initIntervals(effectiveInterval, step, logicName, suffixFormatter);
        this.startInterval = this.effectiveIntervals[0];
        this.effectiveNodes = initEffectiveNodes(this.effectiveIntervals);
    }

    private static Interval[] initIntervals(Range<LocalDateTime> effectiveInterval, Step step, String logicName, DateTimeFormatter suffixFormatter) {
        LocalDateTime lower = step.ofUnit(effectiveInterval.lowerEndpoint());
        LocalDateTime upper = step.ofUnit(effectiveInterval.upperEndpoint());
        List<Interval> intervalList = new ArrayList<>();
        while (!lower.isAfter(upper)) {
            String nodeName = logicName + lower.format(suffixFormatter);
            intervalList.add(new Interval(lower, nodeName));
            lower = step.next(lower);
        }
        return intervalList.toArray(new Interval[intervalList.size()]);
    }

    private static ExactCollection<String> initEffectiveNodes(Interval[] effectiveIntervals) {
        ExactCollection<String> effectiveNodes = new ExactCollection<>(effectiveIntervals.length);
        for (int i = 0; i < effectiveIntervals.length; i++) {
            effectiveNodes.add(i, effectiveIntervals[i].getNode());
        }
        return effectiveNodes;
    }

    public int size() {
        return effectiveIntervals.length;
    }

    public boolean contains(LocalDateTime time) {
        return effectiveInterval.contains(time);
    }

    public Interval getStartInterval() {
        return startInterval;
    }

    @Override
    public Collection<String> getEffectiveNodes() {
        return effectiveNodes;
    }

    @Override
    public String sharding(LocalDateTime shardingValue) {
        if (!contains(shardingValue)) {
            /**
             * throw error?
             */
            return null;
        }
        int offset = step.unitOffset(startInterval.getLower(), shardingValue);
        return effectiveIntervals[offset].getNode();
    }

    @Override
    public Collection<String> sharding(Range<LocalDateTime> shardingValue) {

        if (!effectiveInterval.isConnected(shardingValue)) {
            return Collections.emptyList();
        }

        int maxOffset = size() - 1;
        int lowerOffset = !shardingValue.hasLowerBound() ? 0 : step.unitOffset(startInterval.getLower(), shardingValue.lowerEndpoint());
        if (lowerOffset < 0) {
            lowerOffset = 0;
        }
        int upperOffset = !shardingValue.hasUpperBound() ? maxOffset : step.unitOffset(startInterval.getLower(), shardingValue.upperEndpoint());
        if (upperOffset > maxOffset) {
            upperOffset = maxOffset;
        }

        if (lowerOffset == 0 && upperOffset == maxOffset) {
            return effectiveNodes;
        }

        Interval lastInterval = effectiveIntervals[upperOffset];

        if (lowerOffset == upperOffset) {
            return Collections.singleton(lastInterval.getNode());
        }

        if (shardingValue.hasUpperBound()
                && BoundType.OPEN.equals(shardingValue.upperBoundType())
                && lastInterval.getLower().equals(shardingValue.upperEndpoint())) {
            upperOffset = upperOffset - 1;
        }
        final int nodeSize = upperOffset - lowerOffset + 1;

        //        Set<String> nodes = Sets.newHashSetWithExpectedSize(nodeSize);
        ExactCollection<String> nodes = new ExactCollection<>(nodeSize);

        int idx = 0;
        while (lowerOffset <= upperOffset) {
            Interval interval = effectiveIntervals[lowerOffset];
            nodes.add(idx, interval.getNode());
            lowerOffset++;
            idx++;
        }
        return nodes;
    }

    public static class Interval {

        private final LocalDateTime lower;
        private final String node;

        public Interval(LocalDateTime lower, String node) {
            this.lower = lower;
            this.node = node;
        }

        public LocalDateTime getLower() {
            return lower;
        }

        public String getNode() {
            return node;
        }
    }

    public static class Step {
        public static final int DEFAULT_AMOUNT = 1;

        private final ChronoUnit unit;
        private final int amount;

        public Step(ChronoUnit unit, int amount) {
            this.unit = unit;
            this.amount = amount;
        }

        public ChronoUnit getUnit() {
            return unit;
        }

        public int getAmount() {
            return amount;
        }

        public LocalDateTime next(LocalDateTime previous) {
            return previous.plus(amount, unit);
        }

        /**
         * 按照 {@link #unit} 保留单位时间精度
         *
         * @param time
         * @return
         */
        public LocalDateTime ofUnit(LocalDateTime time) {
            switch (unit) {
                case YEARS: {
                    return LocalDateTime.of(time.getYear(), 1, 1, 0, 0);
                }
                case MONTHS: {
                    return LocalDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0);
                }
                case DAYS: {
                    return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), 0, 0);
                }
                case HOURS: {
                    return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), 0);
                }
                case MINUTES: {
                    return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute());
                }
                case SECONDS: {
                    return LocalDateTime.of(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute(), time.getSecond());
                }
                default:
                    throw new IllegalStateException("Unexpected value: " + unit);
            }
        }

        /**
         * 计算单位偏移量
         * Start with 0
         *
         * @param startInterval 单位时间最小值
         * @param time
         * @return
         */
        public int unitOffset(LocalDateTime startInterval, LocalDateTime time) {
            return getDiffUint(startInterval, time) / amount;
//
//            long until = startInterval.until(time, unit);
//            return (int) until / amount;
        }

        private int getDiffUint(LocalDateTime startInterval, LocalDateTime time) {
            switch (unit) {
                case YEARS: {
                    return getDiffYear(startInterval, time);
                }
                case MONTHS: {
                    return getDiffYearMonth(startInterval, time);
                }
                case DAYS: {
                    return getDiffYearMonthDay(startInterval, time);
                }
                case HOURS: {
                    return getDiffYearMonthDay(startInterval, time) * 24;
                }
                case MINUTES: {
                    return getDiffYearMonthDay(startInterval, time) * 24 * 60;
                }
                case SECONDS: {
                    return getDiffYearMonthDay(startInterval, time) * 24 * 60 * 60;
                }
                default:
                    throw new IllegalStateException("Unexpected value: " + unit);
            }
        }

        private int getDiffYearMonthDay(LocalDateTime startInterval, LocalDateTime time) {
            return (int) (time.toLocalDate().toEpochDay() - startInterval.toLocalDate().toEpochDay());
        }

        private int getDiffYearMonth(LocalDateTime startInterval, LocalDateTime time) {
            return getDiffYear(startInterval, time) * 12 + (time.getMonthValue() - startInterval.getMonthValue());
        }

        private int getDiffYear(LocalDateTime startInterval, LocalDateTime time) {
            return time.getYear() - startInterval.getYear();
        }

        public static Step of(ChronoUnit unit) {
            return new Step(unit, DEFAULT_AMOUNT);
        }

        public static Step of(ChronoUnit unit, int amount) {
            return new Step(unit, amount);
        }
    }
}
