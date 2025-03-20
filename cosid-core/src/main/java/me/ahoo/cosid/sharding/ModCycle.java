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
import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * ModCycle.
 *
 * <p><img src="../doc-files/CosIdModShardingAlgorithm.png" alt="CosIdModShardingAlgorithm"></p>
 *
 * @author ahoo wang
 */
public class ModCycle<T extends Number & Comparable<T>> implements Sharding<T> {
    private final int divisor;
    private final String logicNamePrefix;
    private final ExactCollection<String> effectiveNodes;

    public ModCycle(int divisor, String logicNamePrefix) {
        Preconditions.checkArgument(divisor > 0, "divisor must be greater than 0!");
        this.divisor = divisor;
        this.logicNamePrefix = logicNamePrefix;
        this.effectiveNodes = initNodes(divisor, logicNamePrefix);
    }

    private static ExactCollection<String> initNodes(int divisor, String logicNamePrefix) {
        ExactCollection<String> modNodes = new ExactCollection<>(divisor);

        for (int i = 0; i < divisor; i++) {
            String nodeName = logicNamePrefix + i;
            modNodes.add(i, nodeName);
        }

        return modNodes;
    }

    public int getDivisor() {
        return divisor;
    }

    @Nonnull
    @Override
    public String sharding(T shardingValue) {
        int nodeIdx = (int) (shardingValue.longValue() % divisor);
        return effectiveNodes.get(nodeIdx);
    }

    @Nonnull
    @Override
    public Collection<String> sharding(Range<T> shardingValue) {

        if (Range.all().equals(shardingValue)) {
            return effectiveNodes;
        }

        if (!shardingValue.hasUpperBound()) {
            return effectiveNodes;
        }

        long lower = 0;
        if (shardingValue.hasLowerBound()) {
            long lowerEndpoint = shardingValue.lowerEndpoint().longValue();
            lower = BoundType.OPEN.equals(shardingValue.lowerBoundType()) ? (lowerEndpoint + 1) : lowerEndpoint;
        }

        long upperEndpoint = shardingValue.upperEndpoint().longValue();

        long upper = BoundType.OPEN.equals(shardingValue.upperBoundType()) ? (upperEndpoint - 1) : upperEndpoint;

        final int nodeSize = (int) (upper - lower + 1);

        if (nodeSize == 0) {
            return ExactCollection.empty();
        }

        if (nodeSize >= divisor) {
            return effectiveNodes;
        }

        ExactCollection<String> nodes = new ExactCollection<>(nodeSize);
        int idx = 0;
        while (lower <= upper) {
            int modValue = (int) (lower % divisor);
            String matchedNode = effectiveNodes.get(modValue);
            nodes.add(idx, matchedNode);
            lower++;
            idx++;
        }
        return nodes;
    }

    @Nonnull
    @Override
    public Collection<String> getEffectiveNodes() {
        return effectiveNodes;
    }
}
