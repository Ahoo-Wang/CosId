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

package me.ahoo.cosid.shardingsphere.sharding.mod;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import me.ahoo.cosid.shardingsphere.sharding.Sharding;
import me.ahoo.cosid.shardingsphere.sharding.utils.ExactCollection;

import java.util.Collection;

/**
 * @author ahoo wang
 */
public class ModCycle implements Sharding<Long> {
    private final int divisor;
    private final String logicNamePrefix;
    private final ExactCollection<String> effectiveNodes;

    public ModCycle(int divisor, String logicNamePrefix) {
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

    @Override
    public String sharding(Long shardingValue) {
        int nodeIdx = (int) (shardingValue % divisor);
        return effectiveNodes.get(nodeIdx);
    }

    @Override
    public Collection<String> sharding(Range<Long> shardingValue) {
        long lower, upper;

        if (!shardingValue.hasLowerBound() || !shardingValue.hasUpperBound()) {
            return effectiveNodes;
        }
        /**
         * range
         */
        lower = BoundType.OPEN.equals(shardingValue.lowerBoundType()) ? (shardingValue.lowerEndpoint() + 1) : shardingValue.lowerEndpoint();
        upper = BoundType.OPEN.equals(shardingValue.upperBoundType()) ? (shardingValue.upperEndpoint() - 1) : shardingValue.upperEndpoint();
        if (upper - lower >= divisor) {
            return effectiveNodes;
        }
        final int nodeSize = (int) (upper - lower + 1);
//            Collection<String> nodes = Sets.newHashSetWithExpectedSize(nodeSize);
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

    @Override
    public Collection<String> getEffectiveNodes() {
        return effectiveNodes;
    }
}
