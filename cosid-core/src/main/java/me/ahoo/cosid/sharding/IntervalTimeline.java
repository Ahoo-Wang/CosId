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

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nonnull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Interval Timeline.
 *
 * <p><img src="../doc-files/CosIdIntervalShardingAlgorithm.png" alt="CosIdIntervalShardingAlgorithm"></p>
 *
 * @author ahoo wang
 */
@ThreadSafe
public class IntervalTimeline implements Sharding<LocalDateTime> {

    private final Range<LocalDateTime> effectiveInterval;
    private final IntervalStep step;
    private final Interval startInterval;
    private final Interval[] effectiveIntervals;
    private final String logicNamePrefix;
    private final DateTimeFormatter suffixFormatter;
    private final ExactCollection<String> effectiveNodes;

    public IntervalTimeline(String logicNamePrefix, Range<LocalDateTime> effectiveInterval, IntervalStep step, DateTimeFormatter suffixFormatter) {
        this.effectiveInterval = effectiveInterval;
        this.step = step;
        this.logicNamePrefix = logicNamePrefix;
        this.suffixFormatter = suffixFormatter;
        this.effectiveIntervals = initIntervals(effectiveInterval, step, logicNamePrefix, suffixFormatter);
        this.startInterval = this.effectiveIntervals[0];
        this.effectiveNodes = initEffectiveNodes(this.effectiveIntervals);
    }

    private static Interval[] initIntervals(Range<LocalDateTime> effectiveInterval, IntervalStep step, String logicNamePrefix, DateTimeFormatter suffixFormatter) {
        LocalDateTime lower = step.floorUnit(effectiveInterval.lowerEndpoint());
        LocalDateTime upper = step.floorUnit(effectiveInterval.upperEndpoint());
        List<Interval> intervalList = new ArrayList<>();
        while (!lower.isAfter(upper)) {
            String nodeName = logicNamePrefix + lower.format(suffixFormatter);
            intervalList.add(new Interval(lower, nodeName));
            lower = step.next(lower);
        }
        return intervalList.toArray(new Interval[0]);
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

    @Nonnull
    @Override
    public Collection<String> getEffectiveNodes() {
        return effectiveNodes;
    }

    @Nonnull
    @Override
    public String sharding(LocalDateTime shardingValue) {
        Preconditions.checkArgument(contains(shardingValue), "Sharding value:[%s]: out of bounds:[%s].", shardingValue, effectiveInterval);
        int offset = step.offsetUnit(startInterval.getLower(), shardingValue);
        return effectiveIntervals[offset].getNode();
    }

    @Nonnull
    @Override
    public Collection<String> sharding(Range<LocalDateTime> shardingValue) {

        if (!effectiveInterval.isConnected(shardingValue)) {
            return ExactCollection.empty();
        }
        if (Range.all().equals(shardingValue)) {
            return effectiveNodes;
        }

        int maxOffset = size() - 1;
        int lowerOffset = 0;
        int upperOffset = maxOffset;
        if (shardingValue.hasLowerBound()) {
            LocalDateTime lowerEndpoint = shardingValue.lowerEndpoint();
            if (!lowerEndpoint.isBefore(startInterval.getLower())) {
                lowerOffset = step.offsetUnit(startInterval.getLower(), lowerEndpoint);
            }
        }

        if (shardingValue.hasUpperBound()) {
            LocalDateTime upperEndpoint = shardingValue.upperEndpoint();
            if (!upperEndpoint.isAfter(effectiveInterval.upperEndpoint())) {
                upperOffset = step.offsetUnit(startInterval.getLower(), shardingValue.upperEndpoint());
                Interval lastInterval = effectiveIntervals[upperOffset];
                if (BoundType.OPEN.equals(shardingValue.upperBoundType())
                    && lastInterval.getLower().equals(upperEndpoint)) {
                    if (upperOffset == 0) {
                        return ExactCollection.empty();
                    }
                    upperOffset = upperOffset - 1;
                }
            }
        }

        Interval lastInterval = effectiveIntervals[upperOffset];

        if (lowerOffset == upperOffset) {
            return new ExactCollection<>(lastInterval.getNode());
        }

        if (lowerOffset == 0 && upperOffset == maxOffset) {
            return effectiveNodes;
        }

        final int nodeSize = upperOffset - lowerOffset + 1;

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
}
