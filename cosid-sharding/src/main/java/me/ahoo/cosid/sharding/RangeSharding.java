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

import com.google.common.collect.Range;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * Range-based sharding algorithm interface.
 *
 * <p>Determines which node(s) should handle a range of ID values.
 *
 * @param <T> the type of comparable sharding value
 */
public interface RangeSharding<T extends Comparable<?>> {
    /**
     * Gets the nodes that should handle the given range of IDs.
     *
     * @param shardingValue the range of sharding values
     * @return collection of node names that should handle the range
     */
    @NonNull
    Collection<String> sharding(Range<T> shardingValue);
}
