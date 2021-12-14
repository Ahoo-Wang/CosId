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


import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.shardingsphere.sharding.utils.PropertiesUtil;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * @author ahoo wang
 */
public class ModShardingAlgorithm<T extends Number & Comparable<T>> implements StandardShardingAlgorithm<T> {

    public static final String TYPE = "COSID_MOD";

    public static final String MODULO_KEY = "mod";

    public static final String LOGIC_NAME_PREFIX_KEY = "logic-name-prefix";

    private volatile Properties props = new Properties();
    private volatile ModCycle<T> modCycle;

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

    public ModCycle<T> getModCycle() {
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
        return modCycle.sharding(shardingValue.getValue());
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
        return modCycle.sharding(shardingValue.getValueRange());
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        String divisorStr = PropertiesUtil.getRequiredValue(getProps(), MODULO_KEY);
        int divisor = Integer.parseInt(divisorStr);
        String logicNamePrefix = PropertiesUtil.getRequiredValue(getProps(), LOGIC_NAME_PREFIX_KEY);
        this.modCycle = new ModCycle<>(divisor, logicNamePrefix);
    }
}
