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

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.provider.LazyIdGenerator;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;

import java.time.LocalDateTime;

/**
 * please use {@link CosIdSnowflakeIntervalShardingAlgorithm} instead.
 *
 * @author ahoo wang
 */
@Deprecated
public class SnowflakeFriendlyIntervalShardingAlgorithm extends AbstractZoneIntervalShardingAlgorithm<String> {

    public static final String TYPE = CosIdSnowflakeIntervalShardingAlgorithm.TYPE + "_FRIENDLY";

    private volatile LazyIdGenerator cosIdProvider;

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        super.init();
        cosIdProvider = new LazyIdGenerator(getProps().getOrDefault(CosIdAlgorithm.ID_NAME_KEY, IdGeneratorProvider.SHARE).toString());
    }

    @Override
    protected LocalDateTime convertShardingValue(String shardingValue) {
        return cosIdProvider.asFriendlyId(true).getParser().parse(shardingValue).getTimestamp();
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
