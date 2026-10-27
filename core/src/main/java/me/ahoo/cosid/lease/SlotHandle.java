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

package me.ahoo.cosid.lease;

/**
 * The machine slot of a runtime, shared by its time-based generators (D18, 05-machine-lease.md §4.1).
 */
@FunctionalInterface
public interface SlotHandle {
    /**
     * The current binding: one volatile read. A generator reads it once per id.
     *
     * @return the binding
     */
    SlotBinding current();
}
