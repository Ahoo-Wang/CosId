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

import com.google.common.base.Strings;
import com.google.common.collect.Range;
import me.ahoo.cosid.shardingsphere.sharding.utils.PropertiesUtil;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * @author ahoo wang
 */
public class ModShardingAlgorithm<T extends Comparable<?>> implements StandardShardingAlgorithm<T> {

    public static final String TYPE = "COSID_MOD";

    public static final String MODULO_KEY = "mod";

    public static final String LOGIC_NAME_PREFIX_KEY = "logic-name-prefix";

    private volatile Properties props = new Properties();
    private volatile ModCycle modCycle;

    /**
     * Get type.
     *
     * @return type
     */
    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Get properties.
     *
     * @return properties
     */
    @Override
    public Properties getProps() {
        return props;
    }

    /**
     * Set properties.
     *
     * @param props properties
     */
    @Override
    public void setProps(Properties props) {
        this.props = props;
    }

    public ModCycle getModCycle() {
        return modCycle;
    }

    /**
     * Sharding.
     *
     * @param availableTargetNames available data sources or table names
     * @param shardingValue        sharding value
     * @return sharding result for data source or table name
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<T> shardingValue) {
        Object value = shardingValue.getValue();
        if (value instanceof Integer) {
            return modCycle.sharding(((Integer) shardingValue.getValue()).longValue());
        }
        if (value instanceof Long) {
            return modCycle.sharding((Long) shardingValue.getValue());
        }
        throw new NotSupportModShardingTypeException(Strings.lenientFormat("The current shard type:[%s] is not supported!", shardingValue.getClass()));
    }

    /**
     * Sharding.
     *
     * @param availableTargetNames available data sources or table names
     * @param shardingValue        sharding value
     * @return sharding results for data sources or table names
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<T> shardingValue) {
        Range<?> shardingValueRange = shardingValue.getValueRange();
        Object value = shardingValueRange.hasLowerBound() ? shardingValueRange.lowerEndpoint() : shardingValueRange.upperEndpoint();
        if (value instanceof Integer) {
            Range<Long> rangeShardingValue = Range.range(((Integer) shardingValueRange.lowerEndpoint()).longValue(), shardingValueRange.lowerBoundType(), ((Integer) shardingValueRange.upperEndpoint()).longValue(), shardingValueRange.upperBoundType());
            return modCycle.sharding((rangeShardingValue));
        }
        if (value instanceof Long) {
            Range<Long> rangeShardingValue = Range.range((Long) shardingValueRange.lowerEndpoint(), shardingValueRange.lowerBoundType(), (Long) shardingValueRange.upperEndpoint(), shardingValueRange.upperBoundType());
            return modCycle.sharding((rangeShardingValue));
        }
        throw new NotSupportModShardingTypeException(Strings.lenientFormat("The current shard type:[%s] is not supported!", value.getClass()));
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        String divisorStr = PropertiesUtil.getRequiredValue(getProps(), MODULO_KEY);
        int divisor = Integer.parseInt(divisorStr);
        String logicNamePrefix = PropertiesUtil.getRequiredValue(getProps(), LOGIC_NAME_PREFIX_KEY);
        this.modCycle = new ModCycle(divisor, logicNamePrefix);
    }
}
