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

import me.ahoo.cosid.observe.GeneratorListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the time-based generator events.
 */
final class RecordingListener implements GeneratorListener {
    final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void onSequenceExhausted(String generator) {
        events.add("exhausted");
    }

    @Override
    public void onClockRegression(String generator, long backwardsMillis, String action) {
        events.add(action + ":" + backwardsMillis);
    }

    @Override
    public void onWindowExceeded(String generator) {
        events.add("window");
    }
}
