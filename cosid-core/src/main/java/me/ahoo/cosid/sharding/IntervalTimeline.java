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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ahoo wang
 */
@ThreadSafe
public class IntervalTimeline implements Sharding<LocalDateTime> {

    private final Range<LocalDateTime> effectiveInterval;
    private final IntervalStep step;
    private final Interval startInterval;
    private final Interval[] effectiveIntervals;
    private final String logicName;
    private final DateTimeFormatter suffixFormatter;
    private final ExactCollection<String> effectiveNodes;

    public IntervalTimeline(String logicName, Range<LocalDateTime> effectiveInterval, IntervalStep step, DateTimeFormatter suffixFormatter) {
        this.effectiveInterval = effectiveInterval;
        this.step = step;
        this.logicName = logicName;
        this.suffixFormatter = suffixFormatter;
        this.effectiveIntervals = initIntervals(effectiveInterval, step, logicName, suffixFormatter);
        this.startInterval = this.effectiveIntervals[0];
        this.effectiveNodes = initEffectiveNodes(this.effectiveIntervals);
    }

    private static Interval[] initIntervals(Range<LocalDateTime> effectiveInterval, IntervalStep step, String logicName, DateTimeFormatter suffixFormatter) {
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
}
