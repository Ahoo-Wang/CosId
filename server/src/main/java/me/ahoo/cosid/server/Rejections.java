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

import java.util.Map;

/**
 * The {@value #METER} counter (11-feature-parity.md §7): calls the server turned away to protect itself or the store,
 * tagged {@code reason} = {@value #BUSY} (the blocking pool is full, {@code 503}), {@value #TOO_MANY_KEYS} or
 * {@value #TOO_MANY_NAMESPACES} (the key caps, {@code 429}).
 */
final class Rejections {
    static final String METER = "cosid.server.call.rejections";
    static final String BUSY = "busy";
    static final String TOO_MANY_KEYS = "too_many_keys";
    static final String TOO_MANY_NAMESPACES = "too_many_namespaces";

    private final Map<String, Counter> counters;

    Rejections(MeterRegistry registry) {
        MeterRegistry meters = registry == null ? Metrics.globalRegistry : registry;
        this.counters = Map.of(BUSY, counter(meters, BUSY), TOO_MANY_KEYS, counter(meters, TOO_MANY_KEYS),
            TOO_MANY_NAMESPACES, counter(meters, TOO_MANY_NAMESPACES));
    }

    private static Counter counter(MeterRegistry registry, String reason) {
        return Counter.builder(METER).description("Calls cosid-server turned away").tag("reason", reason).register(registry);
    }

    void record(String reason) {
        counters.get(reason).increment();
    }
}
