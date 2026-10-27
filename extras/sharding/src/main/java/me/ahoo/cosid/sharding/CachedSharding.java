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

package me.ahoo.cosid.sharding;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching wrapper for sharding algorithms.
 *
 * <p>Caches range sharding results to reduce computation overhead
 * for frequently accessed ranges. The cache is bounded: once it holds {@code maximumSize} ranges it is emptied
 * before the next one is added, which keeps lookups lock-free (4.x used a Guava LRU cache).
 *
 * @param <T> the type of comparable sharding value
 * @author ahoo wang
 */
public class CachedSharding<T extends Comparable<? super T>> implements Sharding<T> {
    /**
     * The most ranges kept by default: query ranges come from user input, so the cache must not grow without bound.
     */
    public static final long DEFAULT_MAXIMUM_SIZE = 10_000;

    private final Sharding<T> actual;
    private final long maximumSize;
    private final Map<ShardingRange<T>, Collection<String>> shardingCache = new ConcurrentHashMap<>();

    /**
     * Creates a cached wrapper around the specified sharding algorithm.
     *
     * @param actual the underlying sharding algorithm
     */
    public CachedSharding(Sharding<T> actual) {
        this(actual, DEFAULT_MAXIMUM_SIZE);
    }

    /**
     * Creates a cached wrapper around the specified sharding algorithm, keeping at most {@code maximumSize} ranges.
     *
     * @param actual      the underlying sharding algorithm
     * @param maximumSize the most ranges kept in the cache
     */
    public CachedSharding(Sharding<T> actual, long maximumSize) {
        this.actual = actual;
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize:[" + maximumSize + "] must be greater than 0!");
        }
        this.maximumSize = maximumSize;
    }

    @Override
    public String sharding(T shardingValue) {
        return actual.sharding(shardingValue);
    }

    @Override
    public Collection<String> sharding(ShardingRange<T> shardingValue) {
        Collection<String> cached = shardingCache.get(shardingValue);
        if (cached != null) {
            return cached;
        }
        Collection<String> nodes = actual.sharding(shardingValue);
        if (shardingCache.size() >= maximumSize) {
            shardingCache.clear();
        }
        shardingCache.put(shardingValue, nodes);
        return nodes;
    }

    @Override
    public Collection<String> getEffectiveNodes() {
        return actual.getEffectiveNodes();
    }

}
