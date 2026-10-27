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
 * A range reserved from a {@link RangeStore}: {@code [start, end)} (04-segment.md §1).
 *
 * @param start the first value, inclusive
 * @param end   the end, exclusive; greater than {@code start}
 */
public record Range(long start, long end) {
    public Range {
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative: " + start);
        }
        if (end <= start) {
            throw new IllegalArgumentException("end must be greater than start: [" + start + ", " + end + ")");
        }
    }
}
