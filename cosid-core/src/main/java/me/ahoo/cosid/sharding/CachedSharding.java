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

import com.google.common.annotations.Beta;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Range;
import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * Cached Sharding.
 *
 * @author ahoo wang
 */
@Beta
public class CachedSharding<T extends Comparable<?>> implements Sharding<T> {

    private final Sharding<T> actual;
    private final LoadingCache<Range<T>, Collection<String>> shardingCache;

    public CachedSharding(Sharding<T> actual) {
        this.actual = actual;
        shardingCache = CacheBuilder
            .newBuilder()
            .build(new LoadShardingCache());
    }

    @Nonnull
    @Override
    public String sharding(T shardingValue) {
        return actual.sharding(shardingValue);
    }

    @Nonnull
    @Override
    public Collection<String> sharding(Range<T> shardingValue) {
        return shardingCache.getUnchecked(shardingValue);
    }

    @Nonnull
    @Override
    public Collection<String> getEffectiveNodes() {
        return actual.getEffectiveNodes();
    }

    private class LoadShardingCache extends CacheLoader<Range<T>, Collection<String>> {

        @Override
        public Collection<String> load(@Nonnull Range<T> key) {
            return actual.sharding(key);
        }
    }
}
