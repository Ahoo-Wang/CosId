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

package me.ahoo.cosid.accessor;

/**
 * Fills the {@link CosId} field of an entity when it is not set yet ({@code <= 0} or empty); {@code long}/{@code Long},
 * {@code int}/{@code Integer} (range checked) and {@code String} (through the generator's codec) fields
 * (07-runtime-and-spring.md §3).
 */
@FunctionalInterface
public interface IdFiller {
    void fill(Object entity);
}
