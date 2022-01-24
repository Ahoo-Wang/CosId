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

package me.ahoo.cosid.shardingsphere.sharding.mod;

import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.Sharding;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import me.ahoo.cosid.shardingsphere.sharding.utils.PropertiesUtil;

import com.google.common.annotations.VisibleForTesting;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * @author ahoo wang
 */
public class CosIdModShardingAlgorithm<T extends Number & Comparable<T>> implements StandardShardingAlgorithm<T> {

    public static final String TYPE = CosIdAlgorithm.TYPE_PREFIX + "MOD";

    public static final String MODULO_KEY = "mod";

    private Properties props = new Properties();

    private volatile Sharding<T> sharding;

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

    @VisibleForTesting
    public Sharding<T> getSharding() {
        return sharding;
    }

    /**
     * Sharding.
     *
     * @param availableTargetNames available data sources or table names
     * @param shardingValue        sharding value
     * @return sharding result for data source or table name
     */
    @Override
    public String doSharding(final Collection<String> availableTargetNames, final PreciseShardingValue<T> shardingValue) {
        return sharding.sharding(shardingValue.getValue());
    }

    /**
     * Sharding.
     *
     * @param availableTargetNames available data sources or table names
     * @param shardingValue        sharding value
     * @return sharding results for data sources or table names
     */
    @Override
    public Collection<String> doSharding(final Collection<String> availableTargetNames, final RangeShardingValue<T> shardingValue) {
        return sharding.sharding(shardingValue.getValueRange());
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        String divisorStr = PropertiesUtil.getRequiredValue(getProps(), MODULO_KEY);
        int divisor = Integer.parseInt(divisorStr);
        String logicNamePrefix = PropertiesUtil.getRequiredValue(getProps(), CosIdAlgorithm.LOGIC_NAME_PREFIX_KEY);
        this.sharding = new ModCycle<>(divisor, logicNamePrefix);
    }
}
