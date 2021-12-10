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

import com.google.common.collect.*;

import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class IntervalTimeline {

    private final Range<LocalDateTime> effectiveInterval;
    private final Step step;
    private final Interval startInterval;
    private final Interval[] intervals;
    private final String logicName;
    private final DateTimeFormatter suffixFormatter;
    private final Collection<String> allNodes;

    public IntervalTimeline(String logicName, Range<LocalDateTime> effectiveInterval, Step step, DateTimeFormatter suffixFormatter) {
        this.effectiveInterval = effectiveInterval;
        this.step = step;
        this.logicName = logicName;
        this.suffixFormatter = suffixFormatter;
        this.intervals = initIntervals(effectiveInterval, step, logicName, suffixFormatter);
        this.startInterval = this.intervals[0];
        this.allNodes = Arrays.stream(this.intervals).map(Interval::getNode).collect(ImmutableSet.toImmutableSet());
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

    public int size() {
        return intervals.length;
    }

    public boolean contains(LocalDateTime time) {
        return effectiveInterval.contains(time);
    }

    public Interval getStartInterval() {
        return startInterval;
    }

    public String getNode(LocalDateTime shardingValue) {
        if (!contains(shardingValue)) {
            /**
             * throw error?
             */
            return null;
        }
        int offset = step.unitOffset(startInterval.getLower(), shardingValue);
        return intervals[offset].getNode();
    }

    public Collection<String> getAllNodes() {
        return allNodes;
    }

    /**
     * @param shardingValue
     * @return
     */
    public Collection<String> getRangeNode(Range<LocalDateTime> shardingValue) {

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
            return allNodes;
        }

        Interval lastInterval = intervals[upperOffset];

        if (lowerOffset == upperOffset) {
            return Collections.singleton(lastInterval.getNode());
        }

        if (shardingValue.hasUpperBound()
                && BoundType.OPEN.equals(shardingValue.upperBoundType())
                && lastInterval.getLower().equals(shardingValue.upperEndpoint())) {
            upperOffset = upperOffset - 1;
        }

        Set<String> nodes = Sets.newHashSetWithExpectedSize(upperOffset - lowerOffset + 1);
        while (lowerOffset <= upperOffset) {
            Interval interval = intervals[lowerOffset];
            nodes.add(interval.getNode());
            lowerOffset++;
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
         *
         * @param startInterval 单位时间最小值
         * @param time
         * @return
         */
        public int unitOffset(LocalDateTime startInterval, LocalDateTime time) {
            long until = startInterval.until(time, unit);
            return (int) until / amount;
        }

        public static Step of(ChronoUnit unit) {
            return new Step(unit, DEFAULT_AMOUNT);
        }

        public static Step of(ChronoUnit unit, int amount) {
            return new Step(unit, amount);
        }
    }
}
