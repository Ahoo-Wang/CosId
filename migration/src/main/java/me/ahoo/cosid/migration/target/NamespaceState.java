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

/**
 * The slot metadata and slots of a 5.0 namespace (05 §2, 06 §1).
 *
 * @param limit           the recorded slot limit
 * @param toleranceMillis the recorded tolerance
 * @param slotOffset      the recorded slot offset (api-freeze.md §2.5.1); 0 for metadata without one
 * @param slots           how many of the slots {@code [0, limit)} exist
 * @param minNextFloor    the smallest floor a next holder of those slots would get; {@code Long.MAX_VALUE} without slots
 * @param live            how many of them have a live lease
 */
public record NamespaceState(int limit, long toleranceMillis, int slotOffset, int slots, long minNextFloor, int live) {
}
