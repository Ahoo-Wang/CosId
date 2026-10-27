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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Dispatches the runtime's events to the user's listener (07-runtime-and-spring.md §2): a listener that throws is
 * logged, never fails the generator, refill or renewal that raised the event.
 */
public final class SafeListener implements GeneratorListener {
    private static final Logger log = LoggerFactory.getLogger(SafeListener.class);

    private final GeneratorListener delegate;

    private SafeListener(GeneratorListener delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps {@code listener}; {@link GeneratorListener#NOOP} and an already wrapped listener are returned as they are.
     *
     * @param listener the listener
     * @return the safe listener
     */
    public static GeneratorListener of(GeneratorListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (listener == GeneratorListener.NOOP || listener instanceof SafeListener) {
            return listener;
        }
        return new SafeListener(listener);
    }

    private void failed(String event, RuntimeException e) {
        log.warn("GeneratorListener {} failed on {}", delegate, event, e);
    }

    @Override
    public void onRangeReserved(String generator, String key, Range range, Duration latency) {
        try {
            delegate.onRangeReserved(generator, key, range, latency);
        } catch (RuntimeException e) {
            failed("onRangeReserved", e);
        }
    }

    @Override
    public void onRefillFailed(String generator, String key, Throwable error) {
        try {
            delegate.onRefillFailed(generator, key, error);
        } catch (RuntimeException e) {
            failed("onRefillFailed", e);
        }
    }

    @Override
    public void onBatchResized(String generator, long oldSize, long newSize) {
        try {
            delegate.onBatchResized(generator, oldSize, newSize);
        } catch (RuntimeException e) {
            failed("onBatchResized", e);
        }
    }

    @Override
    public void onSequenceExhausted(String generator) {
        try {
            delegate.onSequenceExhausted(generator);
        } catch (RuntimeException e) {
            failed("onSequenceExhausted", e);
        }
    }

    @Override
    public void onClockRegression(String generator, long backwardsMillis, String action) {
        try {
            delegate.onClockRegression(generator, backwardsMillis, action);
        } catch (RuntimeException e) {
            failed("onClockRegression", e);
        }
    }

    @Override
    public void onWindowExceeded(String generator) {
        try {
            delegate.onWindowExceeded(generator);
        } catch (RuntimeException e) {
            failed("onWindowExceeded", e);
        }
    }

    @Override
    public void onSlotAcquired(SlotLease lease, String reason) {
        try {
            delegate.onSlotAcquired(lease, reason);
        } catch (RuntimeException e) {
            failed("onSlotAcquired", e);
        }
    }

    @Override
    public void onSlotRenewed(SlotLease lease) {
        try {
            delegate.onSlotRenewed(lease);
        } catch (RuntimeException e) {
            failed("onSlotRenewed", e);
        }
    }

    @Override
    public void onSlotRenewFailed(SlotLease lease, Throwable error) {
        try {
            delegate.onSlotRenewFailed(lease, error);
        } catch (RuntimeException e) {
            failed("onSlotRenewFailed", e);
        }
    }

    @Override
    public void onSlotLost(SlotLease lease) {
        try {
            delegate.onSlotLost(lease);
        } catch (RuntimeException e) {
            failed("onSlotLost", e);
        }
    }

    @Override
    public void onSlotReleased(SlotLease lease) {
        try {
            delegate.onSlotReleased(lease);
        } catch (RuntimeException e) {
            failed("onSlotReleased", e);
        }
    }

    @Override
    public void onClockSkew(String namespace, long skewMillis) {
        try {
            delegate.onClockSkew(namespace, skewMillis);
        } catch (RuntimeException e) {
            failed("onClockSkew", e);
        }
    }

    @Override
    public void onDuplicateOwnerKey(String namespace, String ownerKey) {
        try {
            delegate.onDuplicateOwnerKey(namespace, ownerKey);
        } catch (RuntimeException e) {
            failed("onDuplicateOwnerKey", e);
        }
    }

    @Override
    public void onStoreTimeout(String store, String operation) {
        try {
            delegate.onStoreTimeout(store, operation);
        } catch (RuntimeException e) {
            failed("onStoreTimeout", e);
        }
    }
}
