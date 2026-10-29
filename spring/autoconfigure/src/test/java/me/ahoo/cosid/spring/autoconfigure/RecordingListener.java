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

package me.ahoo.cosid.spring.autoconfigure;

import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.Range;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Fires every {@link GeneratorListener} event, and records them.
 */
final class RecordingListener {
    /** The calls {@link #fire} makes: every event, the batch resize twice. */
    static final int EVENTS = 15;
    static final SlotLease LEASE = SlotLease.builder()
        .namespace("ns")
        .slot(3)
        .owner(new Owner("host:1", UUID.randomUUID()))
        .fence(1)
        .expiresAt(2_000)
        .floor(1_000)
        .ceiling(3_000)
        .storeNow(1_000)
        .build();

    private RecordingListener() {
    }

    static GeneratorListener of(List<String> heard) {
        return (GeneratorListener) Proxy.newProxyInstance(RecordingListener.class.getClassLoader(), new Class<?>[] {GeneratorListener.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return "toString".equals(method.getName()) ? "RecordingListener" : method.invoke(heard, args);
                }
                heard.add(method.getName());
                return null;
            });
    }

    static void fire(GeneratorListener listener) {
        listener.onRangeReserved("order", "ns/order", new Range(0, 100), Duration.ofMillis(3));
        listener.onRefillFailed("order", "ns/order", new IllegalStateException("down"));
        listener.onBatchResized("order", 100, 200);
        listener.onBatchResized("order", 200, 100);
        listener.onSequenceExhausted("default");
        listener.onClockRegression("default", 3, "borrow");
        listener.onWindowExceeded("default");
        listener.onSlotAcquired(LEASE, "lowest");
        listener.onSlotRenewed(LEASE);
        listener.onSlotRenewFailed(LEASE, new IllegalStateException("timeout"));
        listener.onSlotLost(LEASE);
        listener.onSlotReleased(LEASE);
        listener.onClockSkew("ns", 7);
        listener.onDuplicateOwnerKey("ns", "host:1");
        listener.onStoreTimeout("mem", "reserve");
    }
}
