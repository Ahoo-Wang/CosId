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

package me.ahoo.cosid.shardingsphere.sharding.interval;

import me.ahoo.cosid.sharding.ExactCollection;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;

import org.apache.shardingsphere.infra.datanode.DataNodeInfo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * @author ahoo wang
 */
class AbstractIntervalShardingAlgorithmTest {
    
    public static final ZoneOffset ZONE_OFFSET_SHANGHAI = ZoneOffset.of("+8");
    public static final LocalDateTime LOWER_DATE_TIME = LocalDateTime.of(2021, 1, 1, 0, 0);
    public static final LocalDateTime UPPER_DATE_TIME = LOWER_DATE_TIME.plusYears(1);
    public static final String LOGIC_NAME = "table_";
    public static final String COLUMN_NAME = "create_time";
    public static final String SUFFIX_FORMATTER_STRING = "yyyyMM";
    public static final DataNodeInfo DATA_NODE_INFO=new DataNodeInfo(LOGIC_NAME, 6, '0');
    public static final DateTimeFormatter SUFFIX_FORMATTER = DateTimeFormatter.ofPattern(SUFFIX_FORMATTER_STRING);
    public static final ExactCollection<String> ALL_NODES = new ExactCollection<>("table_202101", "table_202102", "table_202103", "table_202104", "table_202105",
        "table_202106", "table_202107", "table_202108", "table_202109", "table_202110", "table_202111", "table_202112", "table_202201");

    Properties getProps() {
        Properties properties = new Properties();
        properties.setProperty(CosIdAlgorithm.LOGIC_NAME_PREFIX_KEY, LOGIC_NAME);
        properties.setProperty(CosIdIntervalShardingAlgorithm.ZONE_ID_KEY, "Asia/Shanghai");
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, LOWER_DATE_TIME.format(AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_FORMATTER));
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, UPPER_DATE_TIME.format(AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_FORMATTER));
        properties.setProperty(AbstractIntervalShardingAlgorithm.SHARDING_SUFFIX_FORMAT_KEY, SUFFIX_FORMATTER_STRING);
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_UNIT_KEY, "MONTHS");
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_AMOUNT_KEY, "1");
        return properties;
    }


}
