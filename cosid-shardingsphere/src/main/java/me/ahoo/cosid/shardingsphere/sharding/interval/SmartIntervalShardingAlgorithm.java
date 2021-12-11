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

import com.google.common.base.Strings;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * @author ahoo wang
 */
public class SmartIntervalShardingAlgorithm extends AbstractZoneIntervalShardingAlgorithm<Comparable<?>> {
    public static final String TYPE = "COSID_INTERVAL";
    public static final String TIMESTAMP_SECOND_TYPE = "SECOND";
    /**
     * MS
     */
    public static final String TIMESTAMP_TYPE_KEY = "ts-type";

    private volatile boolean isSecondTs = false;

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        super.init();
        if (getProps().containsKey(TIMESTAMP_TYPE_KEY)
                && TIMESTAMP_SECOND_TYPE.equalsIgnoreCase(getProps().getProperty(TIMESTAMP_TYPE_KEY))) {
            isSecondTs = true;
        }
    }

    @Override
    protected LocalDateTime convertShardingValue(Comparable<?> shardingValue) {
        if (shardingValue instanceof LocalDateTime) {
            return (LocalDateTime) shardingValue;
        }

        if (shardingValue instanceof Date) {
            return LocalDateTimeConvert.fromDate((Date) shardingValue, getZoneId());
        }

        if (shardingValue instanceof Long) {
            if (isSecondTs) {
                return LocalDateTimeConvert.fromTimestampSecond((Long) shardingValue, getZoneId());
            }
            return LocalDateTimeConvert.fromTimestamp((Long) shardingValue, getZoneId());
        }
        throw new NotSupportIntervalShardingTypeException(Strings.lenientFormat("The current shard type:[%s] is not supported!", shardingValue.getClass()));
    }

    /**
     * Get type.
     *
     * @return type
     */
    @Override
    public String getType() {
        return TYPE;
    }
}
