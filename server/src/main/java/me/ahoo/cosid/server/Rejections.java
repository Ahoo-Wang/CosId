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

package me.ahoo.cosid.server;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

/**
 * The {@value #METER} counter (11-feature-parity.md §7): calls the server turned away to protect itself or the store,
 * tagged {@code reason} = {@value #BUSY} (the blocking pool is full, {@code 503}) or {@value #TOO_MANY_KEYS} (the
 * key cap, {@code 429}).
 */
final class Rejections {
    static final String METER = "cosid.server.call.rejections";
    static final String BUSY = "busy";
    static final String TOO_MANY_KEYS = "too_many_keys";

    private final Counter busy;
    private final Counter tooManyKeys;

    Rejections(MeterRegistry registry) {
        MeterRegistry meters = registry == null ? Metrics.globalRegistry : registry;
        this.busy = counter(meters, BUSY);
        this.tooManyKeys = counter(meters, TOO_MANY_KEYS);
    }

    private static Counter counter(MeterRegistry registry, String reason) {
        return Counter.builder(METER).description("Calls cosid-server turned away").tag("reason", reason).register(registry);
    }

    void record(String reason) {
        (BUSY.equals(reason) ? busy : tooManyKeys).increment();
    }
}
