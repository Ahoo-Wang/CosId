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

package me.ahoo.cosid.lease;

import me.ahoo.cosid.internal.Ticker;
import me.ahoo.cosid.store.tck.ManualClock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Elapsed time the test moves; {@link #sleep(long)} returns at once after moving it, and every move also moves the
 * linked clocks, so a keeper waiting for a store expiry sees the store clock pass it.
 */
final class ManualTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong();
    private final List<ManualClock> linked;

    ManualTicker(ManualClock... linked) {
        this.linked = List.of(linked);
    }

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    @Override
    public void sleep(long sleepNanos) {
        advanceNanos(sleepNanos);
    }

    void advance(Duration amount) {
        advanceNanos(amount.toNanos());
    }

    private void advanceNanos(long amount) {
        long before = nanos.getAndAdd(amount);
        long millis = (before + amount) / 1_000_000 - before / 1_000_000;
        for (ManualClock clock : linked) {
            clock.advance(Duration.ofMillis(millis));
        }
    }
}
