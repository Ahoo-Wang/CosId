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

package me.ahoo.cosid;

import java.util.Objects;

/**
 * An id of a periodic segment: the period it belongs to and its value within the period (04-segment.md §3).
 *
 * @param period the period key, such as {@code "20261026"}
 * @param value  the value within the period
 */
public record GroupedId(String period, long value) {
    public GroupedId {
        Objects.requireNonNull(period, "period");
    }
}
