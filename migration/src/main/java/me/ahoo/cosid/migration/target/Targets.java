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

package me.ahoo.cosid.migration.target;

import me.ahoo.cosid.migration.StoreLocation;

/**
 * Opens the {@link TargetAdmin} of a 5.0 location.
 */
public final class Targets {
    private Targets() {
    }

    /**
     * Opens the admin path of the location's type.
     *
     * @param location the 5.0 store
     * @return the admin path; the caller closes it
     */
    public static TargetAdmin open(StoreLocation location) {
        return switch (location.type()) {
            case StoreLocation.JDBC -> new JdbcTarget(location);
            case StoreLocation.REDIS -> new RedisTarget(location);
            case StoreLocation.MONGO -> new MongoTarget(location);
            case StoreLocation.ZOOKEEPER -> new ZookeeperTarget(location);
            default -> throw new IllegalArgumentException("Unknown store type " + location.type());
        };
    }
}
