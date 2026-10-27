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

package me.ahoo.cosid.time;

import java.time.Instant;
import java.util.Objects;

/**
 * The parts of a time-based id (03-core-model.md §2).
 *
 * @param time the time, in units of the layout since its epoch
 * @param node the node, the machine slot
 * @param seq  the sequence
 */
public record IdParts(long time, int node, long seq) {
    /**
     * The instant of {@link #time()}.
     *
     * @param layout the layout the parts come from
     * @return the instant
     */
    public Instant instant(Layout layout) {
        Objects.requireNonNull(layout, "layout");
        return layout.epoch().plus(time, layout.unit());
    }
}
