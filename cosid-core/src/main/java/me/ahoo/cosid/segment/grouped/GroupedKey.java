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

package me.ahoo.cosid.segment.grouped;

import me.ahoo.cosid.segment.IdSegment;
import me.ahoo.cosid.util.Clock;

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * Key used for grouping/sharding of ID segments.
 *
 * <p>Represents a logical grouping key with an optional TTL for time-based
 * sharding strategies (e.g., daily or monthly buckets).
 *
 * @author ahoo wang
 */
public final class GroupedKey {
    /**
     * Sentinel value indicating no grouping.
     */
    public static final GroupedKey NEVER = new GroupedKey("", IdSegment.TIME_TO_LIVE_FOREVER);
    private final String key;
    private final long ttlAt;

    /**
     * Creates a new GroupedKey.
     *
     * @param key   the grouping key (e.g., "2024-01" for monthly)
     * @param ttlAt the time-to-live expiration timestamp in seconds
     */
    public GroupedKey(String key, long ttlAt) {
        this.key = key;
        this.ttlAt = ttlAt;
    }

    /**
     * Gets the grouping key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the TTL expiration timestamp.
     *
     * @return TTL timestamp in seconds
     * @see IdSegment#getTtl()
     */
    public long getTtlAt() {
        return ttlAt;
    }

    /**
     * Calculates remaining TTL from current time.
     *
     * @return remaining TTL in seconds, or 0 if expired
     */
    public long ttl() {
        return ttlAt - Clock.CACHE.secondTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroupedKey that = (GroupedKey) o;
        return ttlAt == that.ttlAt && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, ttlAt);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("key", key)
            .add("ttlAt", ttlAt)
            .toString();
    }

    /**
     * Creates a GroupedKey that never expires.
     *
     * @param key the grouping key
     * @return a forever GroupedKey
     */
    public static GroupedKey forever(String key) {
        return new GroupedKey(key, IdSegment.TIME_TO_LIVE_FOREVER);
    }
}

