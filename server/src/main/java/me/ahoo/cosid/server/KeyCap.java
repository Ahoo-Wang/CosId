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

import me.ahoo.cosid.store.remote.RemoteApi;

import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * The cap on range keys (08-remote.md §2, 4.x {@code max-distributors}), per client, in memory, per server instance:
 * it bounds the keys one caller makes this instance pass on to the store.
 *
 * <ul>
 *     <li>A client may use at most {@code max-keys-per-namespace} keys in one namespace, and keys in at most
 *     {@code max-namespaces-per-client} namespaces (a key without {@code /} is a namespace of its own, so this bounds
 *     those too); a new key past either cap is a {@code 429}. Each client counts on its own: one cannot use up a shared
 *     namespace for the others.</li>
 *     <li>A key counts from its admission; when the reserve fails (store error, 503, ...) it is forgotten again, so
 *     only keys that the store reserved stay counted.</li>
 *     <li>A key unused for {@code key-idle-timeout} no longer counts (least recently used first).</li>
 * </ul>
 */
final class KeyCap {
    private static final Runnable NOTHING = () -> {
    };

    private final int maxKeysPerNamespace;
    private final int maxNamespaces;
    private final long idleNanos;
    private final LongSupplier nanoTime;
    private final Rejections rejections;
    private final ConcurrentMap<Grant, Client> clients = new ConcurrentHashMap<>();

    KeyCap(ServerProperties.Limits limits, Rejections rejections) {
        this(limits.maxKeysPerNamespace(), limits.maxNamespacesPerClient(), limits.keyIdleTimeout(), System::nanoTime, rejections);
    }

    KeyCap(int maxKeysPerNamespace, int maxNamespaces, Duration idleTimeout, LongSupplier nanoTime, Rejections rejections) {
        this.maxKeysPerNamespace = maxKeysPerNamespace;
        this.maxNamespaces = maxNamespaces;
        this.idleNanos = idleTimeout.toNanos();
        this.nanoTime = nanoTime;
        this.rejections = rejections;
    }

    /**
     * Admits a key for a reserve of the client.
     *
     * @param grant the client
     * @param key   the range key
     * @return what forgets the key again when the reserve failed; it does nothing for a key that was already counted
     * @throws ApiException {@code 429} when the key is new and past a cap
     */
    Runnable admit(Grant grant, String key) {
        if (maxKeysPerNamespace == 0 && maxNamespaces == 0) {
            return NOTHING;
        }
        return clients.computeIfAbsent(grant, ignored -> new Client()).admit(key);
    }

    /** The keys of one client: least recently used first, and their count per namespace. */
    private final class Client {
        private final LinkedHashMap<String, Long> lastUsed = new LinkedHashMap<>(16, 0.75f, true);
        private final Map<String, Integer> perNamespace = new HashMap<>();

        synchronized Runnable admit(String key) {
            long now = nanoTime.getAsLong();
            evictIdle(now);
            if (lastUsed.containsKey(key)) {
                lastUsed.put(key, now);
                return NOTHING;
            }
            String namespace = Requests.namespaceOf(key);
            Integer count = perNamespace.get(namespace);
            if (count == null && maxNamespaces > 0 && perNamespace.size() >= maxNamespaces) {
                rejections.record(Rejections.TOO_MANY_NAMESPACES);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, RemoteApi.TOO_MANY_NAMESPACES,
                    "This client already uses range keys in " + maxNamespaces + " namespaces (" + ServerProperties.PREFIX + ".limits.max-namespaces-per-client)");
            }
            if (count != null && maxKeysPerNamespace > 0 && count >= maxKeysPerNamespace) {
                rejections.record(Rejections.TOO_MANY_KEYS);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, RemoteApi.TOO_MANY_KEYS,
                    "This client already uses " + maxKeysPerNamespace + " range keys in [" + namespace + "] (" + ServerProperties.PREFIX
                        + ".limits.max-keys-per-namespace)");
            }
            lastUsed.put(key, now);
            perNamespace.merge(namespace, 1, Integer::sum);
            return () -> forget(key);
        }

        synchronized void forget(String key) {
            if (lastUsed.remove(key) != null) {
                uncount(key);
            }
        }

        private void evictIdle(long now) {
            if (idleNanos == 0) {
                return;
            }
            Iterator<Map.Entry<String, Long>> oldest = lastUsed.entrySet().iterator();
            while (oldest.hasNext()) {
                Map.Entry<String, Long> entry = oldest.next();
                if (now - entry.getValue() < idleNanos) {
                    return;
                }
                oldest.remove();
                uncount(entry.getKey());
            }
        }

        private void uncount(String key) {
            perNamespace.computeIfPresent(Requests.namespaceOf(key), (namespace, count) -> count == 1 ? null : count - 1);
        }
    }
}
