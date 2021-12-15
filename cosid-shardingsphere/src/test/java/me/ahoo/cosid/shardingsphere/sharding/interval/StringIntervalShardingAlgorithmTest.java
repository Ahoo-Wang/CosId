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

import com.google.common.collect.Range;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.sharding.ExactCollection;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ahoo wang
 */
class StringIntervalShardingAlgorithmTest extends AbstractIntervalShardingAlgorithmTest {
    public final static String ID_NAME = "test_snowflake_friendly_interval";
    StringIntervalShardingAlgorithm shardingAlgorithm;
    SnowflakeFriendlyId friendlyId;

    @BeforeEach
    void init() {
        shardingAlgorithm = new StringIntervalShardingAlgorithm();
        Properties properties = getProps();
        properties.setProperty(CosIdAlgorithm.ID_NAME_KEY, ID_NAME);
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
        friendlyId = new DefaultSnowflakeFriendlyId(new MillisecondSnowflakeId(1));
        DefaultIdGeneratorProvider.INSTANCE.set(ID_NAME, friendlyId);
    }

    @Test
    public void doShardingPrecise() {
        String expected = "table_202112";
        PreciseShardingValue shardingValue = new PreciseShardingValue<>(LOGIC_NAME, COLUMN_NAME, "2021-12-09 22:00:00");
        String actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }

    @Test
    public void doShardingRange() {
        ExactCollection<String> expected = new ExactCollection<>("table_202111","table_202112");
        RangeShardingValue shardingValue = new RangeShardingValue<>(LOGIC_NAME, COLUMN_NAME, Range.closed("2021-11-09 22:00:00", "2021-12-09 22:00:00"));
        Collection<String> actual = shardingAlgorithm.doSharding(ALL_NODES, shardingValue);
        assertEquals(expected, actual);
    }

}
