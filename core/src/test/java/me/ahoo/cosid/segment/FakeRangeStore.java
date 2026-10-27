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

import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A test-only range store with injectable latency, failures and lost responses, until the TCK's InMemoryStore and
 * fault wrappers (R1-C) are merged.
 */
final class FakeRangeStore implements RangeStore {
    private final Map<String, Long> values = new HashMap<>();
    private final Set<String> sealed = new HashSet<>();
    private final List<Range> reserved = new ArrayList<>();
    private final List<String> sealCalls = new ArrayList<>();
    final AtomicInteger reserves = new AtomicInteger();
    /** Thrown instead of reserving. */
    volatile RuntimeException failure;
    /** Reserve, then throw: the caller never learns the range (a lost response). */
    volatile boolean loseResponses;
    /** Each reservation waits for it before reserving. */
    volatile CountDownLatch gate;
    /** Each reservation sleeps this long before reserving. */
    volatile long latencyMillis;
    /** Called with the key at the start of each reservation, on the reserving thread. */
    volatile Consumer<String> onReserve = key -> { };
    /** The largest end a range may have. */
    volatile long limit = Long.MAX_VALUE;
    /** Returned instead of reserving, once: a misbehaving store. */
    volatile Range override;

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        reserves.incrementAndGet();
        onReserve.accept(key);
        CountDownLatch latch = gate;
        try {
            if (latch != null && !latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gate never opened");
            }
            if (latencyMillis > 0) {
                Thread.sleep(latencyMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        RuntimeException fail = failure;
        if (fail != null) {
            throw fail;
        }
        Range range;
        synchronized (this) {
            if (override != null) {
                range = override;
                override = null;
                reserved.add(range);
                return range;
            }
            if (sealed.contains(key)) {
                throw new RangeSealedException(key);
            }
            long start = values.getOrDefault(key, initialOffset);
            if (start > limit - size) {
                throw new RangeExhaustedException(key);
            }
            range = new Range(start, start + size);
            values.put(key, range.end());
            reserved.add(range);
        }
        if (loseResponses) {
            throw new IllegalStateException("response lost");
        }
        return range;
    }

    @Override
    public synchronized boolean seal(String key) {
        sealCalls.add(key);
        sealed.add(key);
        return true;
    }

    synchronized void sealNow(String key) {
        sealed.add(key);
    }

    synchronized boolean exists(String key) {
        return values.containsKey(key);
    }

    synchronized List<Range> reserved() {
        return List.copyOf(reserved);
    }

    synchronized List<String> sealCalls() {
        return List.copyOf(sealCalls);
    }
}
