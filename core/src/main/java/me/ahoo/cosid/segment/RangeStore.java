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

package me.ahoo.cosid.segment;

/**
 * Reserves ranges atomically, the segment primitive of a store (04-segment.md §1).
 *
 * <p>Invariants, verified by the TCK: the ranges of a key are pairwise disjoint and strictly increasing in completion
 * order (S1); the first range of a new key starts at {@code initialOffset}, once (S2); the returned range is
 * authoritative (S3); gaps are allowed, duplicates never (S4); values stay in {@code [0, Long.MAX_VALUE)} (S5); a sealed
 * key is never recreated (S6).
 */
public interface RangeStore extends AutoCloseable {
    /**
     * Atomically reserves {@code [start, start + size)}; a missing key is created at {@code initialOffset}.
     *
     * @param key           the range key, {@code {namespace}/{generator}} or {@code {namespace}/{generator}/{period}}
     * @param size          the size of the range, positive
     * @param initialOffset where the first range of a new key starts
     * @return the reserved range
     * @throws me.ahoo.cosid.RangeSealedException    if the key is sealed
     * @throws me.ahoo.cosid.RangeExhaustedException if the range would pass {@code Long.MAX_VALUE}
     */
    Range reserve(String key, long size, long initialOffset);

    /**
     * Seals the key: every later {@link #reserve} fails and the key's value is kept. Optional.
     *
     * @param key the range key
     * @return whether the store sealed the key; false if it does not support sealing
     */
    default boolean seal(String key) {
        return false;
    }

    @Override
    default void close() {
    }
}
