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

import me.ahoo.cosid.observe.GeneratorListener;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the segment events.
 */
final class RecordingListener implements GeneratorListener {
    final List<Range> reserved = new CopyOnWriteArrayList<>();
    final List<Throwable> failures = new CopyOnWriteArrayList<>();
    final List<long[]> resized = new CopyOnWriteArrayList<>();

    @Override
    public void onRangeReserved(String generator, String key, Range range, Duration latency) {
        reserved.add(range);
    }

    @Override
    public void onRefillFailed(String generator, String key, Throwable error) {
        failures.add(error);
    }

    @Override
    public void onBatchResized(String generator, long oldSize, long newSize) {
        resized.add(new long[] {oldSize, newSize});
    }
}
