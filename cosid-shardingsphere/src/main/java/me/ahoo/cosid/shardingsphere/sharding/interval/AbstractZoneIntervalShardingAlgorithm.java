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

import java.time.ZoneId;

/**
 * @author ahoo wang
 */
public abstract class AbstractZoneIntervalShardingAlgorithm<T extends Comparable<?>> extends AbstractIntervalShardingAlgorithm<T> {

    public static final String ZONE_ID_KEY = "zone-id";

    private ZoneId zoneId = ZoneId.systemDefault();

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        super.init();
        if (getProps().containsKey(ZONE_ID_KEY)) {
            zoneId = ZoneId.of(getRequiredValue(ZONE_ID_KEY));
        }
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
