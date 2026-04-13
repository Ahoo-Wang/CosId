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

/**
 * Interface for objects that can be grouped for sharding purposes.
 *
 * <p>Implementations return a {@link GroupedKey} that represents the sharding
 * key for this object. This is used by segmented ID generators to organize
 * IDs into logical groups.
 */
public interface Grouped {
    /**
     * Gets the grouping key for this object.
     *
     * <p>Default implementation returns {@link GroupedKey#NEVER}, indicating
     * this object should not be grouped.
     *
     * @return the grouping key
     */
    default GroupedKey group() {
        return GroupedKey.NEVER;
    }
}
