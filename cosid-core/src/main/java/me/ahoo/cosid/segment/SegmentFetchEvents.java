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

import me.ahoo.cosid.event.CosIdEvents;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Times a segment fetch and publishes it as {@link me.ahoo.cosid.event.CosIdEventListener#onSegmentFetch}.
 */
final class SegmentFetchEvents {
    private SegmentFetchEvents() {
    }
    
    static <T> T timed(String namespacedName, Supplier<T> fetch) {
        long started = System.nanoTime();
        try {
            T fetched = fetch.get();
            CosIdEvents.listener().onSegmentFetch(namespacedName, Duration.ofNanos(System.nanoTime() - started), null);
            return fetched;
        } catch (RuntimeException fetchException) {
            CosIdEvents.listener().onSegmentFetch(namespacedName, Duration.ofNanos(System.nanoTime() - started), fetchException);
            throw fetchException;
        }
    }
}
