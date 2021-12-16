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

import me.ahoo.cosid.util.LocalDateTimeConvert;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * support types: {@link Date}/{@link java.sql.Date}/{@link java.sql.Timestamp}
 *
 * @author ahoo wang
 */
public class DateIntervalShardingAlgorithm extends AbstractZoneIntervalShardingAlgorithm<Date> {

    public static final String TYPE = TYPE_PREFIX + "DATE";

    /**
     * Get type.
     *
     * @return type
     */
    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected LocalDateTime convertShardingValue(Date shardingValue) {
        return LocalDateTimeConvert.fromDate(shardingValue, getZoneId());
    }

}
