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

package me.ahoo.cosid.migration.source;

import me.ahoo.cosid.migration.StoreLocation;

/**
 * Opens the {@link SourceReader} of a 4.x location.
 */
public final class Sources {
    private Sources() {
    }

    /**
     * Opens the reader of the location's type.
     *
     * @param location the 4.x store
     * @return the reader; the caller closes it
     */
    public static SourceReader open(StoreLocation location) {
        return switch (location.type()) {
            case StoreLocation.JDBC -> new JdbcSource(location);
            case StoreLocation.REDIS -> new RedisSource(location);
            case StoreLocation.MONGO -> new MongoSource(location);
            case StoreLocation.ZOOKEEPER -> new ZookeeperSource(location);
            default -> throw new IllegalArgumentException("Unknown store type " + location.type());
        };
    }
}
