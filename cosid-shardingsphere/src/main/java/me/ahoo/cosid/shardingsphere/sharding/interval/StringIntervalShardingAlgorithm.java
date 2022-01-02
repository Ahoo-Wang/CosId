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
import java.time.format.DateTimeFormatter;

/**
 * please use {@link SmartIntervalShardingAlgorithm} instead.
 *
 * @author ahoo wang
 */
@Deprecated
public class StringIntervalShardingAlgorithm extends AbstractIntervalShardingAlgorithm<String> {

    public static final String TYPE = TYPE_PREFIX + "STRING";
    public static final String DATE_TIME_PATTERN_KEY = "datetime-pattern";
    private volatile DateTimeFormatter dateTimeFormatter;

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        super.init();
        final String dateTimePattern = getProps().getProperty(DATE_TIME_PATTERN_KEY, DEFAULT_DATE_TIME_PATTERN);
        dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimePattern);
    }

    @Override
    protected LocalDateTime convertShardingValue(String shardingValue) {
        return LocalDateTimeConvert.fromString(shardingValue, dateTimeFormatter);
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
