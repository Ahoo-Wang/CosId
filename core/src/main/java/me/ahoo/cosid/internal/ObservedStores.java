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

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.StoreTimeoutException;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The stores as the runtime hands them to its generators and lease keeper: every call that fails with a
 * {@link StoreTimeoutException} (itself or as a cause) is reported to {@link GeneratorListener#onStoreTimeout} with the
 * store's name and the operation, then rethrown unchanged. Nothing else is added to a call.
 */
public final class ObservedStores {
    public static final String RESERVE = "reserve";
    public static final String SEAL = "seal";
    public static final String ACQUIRE = "acquire";
    public static final String RENEW = "renew";
    public static final String RELEASE = "release";

    private ObservedStores() {
    }

    /**
     * The range store {@code name}, reporting its timeouts.
     *
     * @param name     the store name
     * @param store    the range store
     * @param listener the listener
     * @return the observed store
     */
    public static RangeStore ranges(String name, RangeStore store, GeneratorListener listener) {
        Objects.requireNonNull(store, "store");
        Observer observer = new Observer(name, listener);
        return new RangeStore() {
            @Override
            public Range reserve(String key, long size, long initialOffset) {
                return observer.call(RESERVE, () -> store.reserve(key, size, initialOffset));
            }

            @Override
            public boolean seal(String key) {
                return observer.call(SEAL, () -> store.seal(key));
            }

            @Override
            public void close() {
                store.close();
            }
        };
    }

    /**
     * The slot lease store {@code name}, reporting its timeouts.
     *
     * @param name     the store name
     * @param store    the slot lease store
     * @param listener the listener
     * @return the observed store
     */
    public static SlotLeaseStore slots(String name, SlotLeaseStore store, GeneratorListener listener) {
        Objects.requireNonNull(store, "store");
        Observer observer = new Observer(name, listener);
        return new SlotLeaseStore() {
            @Override
            public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
                return observer.call(ACQUIRE, () -> store.acquire(namespace, owner, limit, ttl, tolerance, preferredSlot));
            }

            @Override
            public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
                return observer.call(RENEW, () -> store.renew(lease, ttl, tolerance));
            }

            @Override
            public boolean release(SlotLease lease, long lastIssued) {
                return observer.call(RELEASE, () -> store.release(lease, lastIssued));
            }

            @Override
            public void close() {
                store.close();
            }
        };
    }

    private record Observer(String name, GeneratorListener listener) {
        Observer {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(listener, "listener");
        }

        <T> T call(String operation, Supplier<T> call) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (StoreTimeoutException.isTimeout(e)) {
                    listener.onStoreTimeout(name, operation);
                }
                throw e;
            }
        }
    }
}
