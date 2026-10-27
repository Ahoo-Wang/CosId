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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The per-namespace cap on range keys (08-remote.md §2, 4.x {@code max-distributors}): a namespace may use at most
 * {@code max-keys-per-namespace} distinct keys through this server instance; a new key past the cap is a {@code 429}.
 * The count lives in memory, per instance: it bounds what one instance passes on to the store.
 */
final class KeyCap {
    private final int max;
    private final Rejections rejections;
    private final ConcurrentMap<String, Set<String>> keys = new ConcurrentHashMap<>();

    KeyCap(int max, Rejections rejections) {
        this.max = max;
        this.rejections = rejections;
    }

    void admit(String key) {
        if (max == 0) {
            return;
        }
        String namespace = Requests.namespaceOf(key);
        Set<String> known = keys.computeIfAbsent(namespace, ignored -> new HashSet<>());
        synchronized (known) {
            if (known.contains(key)) {
                return;
            }
            if (known.size() >= max) {
                rejections.record(Rejections.TOO_MANY_KEYS);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, RemoteApi.TOO_MANY_KEYS,
                    "Namespace [" + namespace + "] already uses " + max + " range keys (" + ServerProperties.PREFIX + ".limits.max-keys-per-namespace)");
            }
            known.add(key);
        }
    }
}
