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

import java.time.LocalDateTime;

/**
 * @author ahoo wang
 */
public class LocalDateTimeIntervalShardingAlgorithm extends AbstractIntervalShardingAlgorithm<LocalDateTime> {
    public static final String TYPE = PREFIX_TYPE + "LDT";

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
    protected LocalDateTime convertShardingValue(LocalDateTime shardingValue) {
        return shardingValue;
    }

    @Override
    protected Range<LocalDateTime> convertRangeShardingValue(Range<LocalDateTime> shardingValue) {
        return shardingValue;
    }
}
