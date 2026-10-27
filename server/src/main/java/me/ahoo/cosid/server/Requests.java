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

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The checks of request fields (08-remote.md §2): presence, length and character set of keys, namespaces and owner
 * keys, and the {@code cosid.server.limits}. A failed check is a {@code 400}.
 */
final class Requests {
    /** A namespace: what every backend accepts (JDBC {@code varchar(100)}, no {@code /} for ZooKeeper). */
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");
    /** A range key: segments separated by single slashes, without blanks or control characters. */
    private static final Pattern KEY = Pattern.compile("[^/\\s\\p{Cntrl}]+(/[^/\\s\\p{Cntrl}]+)*");
    private static final int MAX_KEY_LENGTH = 255;

    private final ServerProperties.Limits limits;

    Requests(ServerProperties.Limits limits) {
        this.limits = limits;
    }

    /**
     * The namespace of a range key: its first segment ({@code {namespace}/{generator}[/{period}]}).
     *
     * @param key the range key
     * @return the namespace
     */
    static String namespaceOf(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw ApiException.badRequest(field + " is required");
        }
        return value;
    }

    String key(String key) {
        required(key, "key");
        if (key.length() > MAX_KEY_LENGTH || !KEY.matcher(key).matches()) {
            throw ApiException.badRequest("key must be at most " + MAX_KEY_LENGTH + " characters, slash-separated segments without blanks: " + abbreviate(key));
        }
        for (String segment : key.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw ApiException.badRequest("key must not have a '.' or '..' segment: " + abbreviate(key));
            }
        }
        return key;
    }

    String namespace(String namespace) {
        required(namespace, "namespace");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw ApiException.badRequest("namespace must be 1 to 100 letters, digits, '.', '_', ':' or '-', starting with a letter or digit: "
                + abbreviate(namespace));
        }
        return namespace;
    }

    String ownerKey(String ownerKey) {
        required(ownerKey, "ownerKey");
        if (ownerKey.isBlank() || ownerKey.length() > MAX_KEY_LENGTH || ownerKey.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("ownerKey must be 1 to " + MAX_KEY_LENGTH + " characters without control characters");
        }
        return ownerKey;
    }

    static UUID incarnation(String incarnation) {
        required(incarnation, "incarnation");
        try {
            return UUID.fromString(incarnation);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("incarnation must be a UUID: " + abbreviate(incarnation));
        }
    }

    long size(Long size) {
        required(size, "size");
        if (size <= 0 || size > limits.maxReserve()) {
            throw ApiException.badRequest("size must be in [1, " + limits.maxReserve() + "]: " + size);
        }
        return size;
    }

    static long notNegative(Long value, String field) {
        required(value, field);
        if (value < 0) {
            throw ApiException.badRequest(field + " must not be negative: " + value);
        }
        return value;
    }

    int limit(Integer limit) {
        required(limit, "limit");
        if (limit <= 0 || limit > limits.maxSlotLimit()) {
            throw ApiException.badRequest("limit must be in [1, " + limits.maxSlotLimit() + "]: " + limit);
        }
        return limit;
    }

    Duration ttl(Long ttlMs) {
        required(ttlMs, "ttlMs");
        Duration ttl = Duration.ofMillis(ttlMs);
        if (ttl.compareTo(limits.minTtl()) < 0 || ttl.compareTo(limits.maxTtl()) > 0) {
            throw ApiException.badRequest("ttlMs must be in [" + limits.minTtl().toMillis() + ", " + limits.maxTtl().toMillis() + "]: " + ttlMs);
        }
        return ttl;
    }

    Duration tolerance(Long toleranceMs) {
        required(toleranceMs, "toleranceMs");
        Duration tolerance = Duration.ofMillis(toleranceMs);
        if (tolerance.isNegative() || tolerance.compareTo(limits.maxTolerance()) > 0) {
            throw ApiException.badRequest("toleranceMs must be in [0, " + limits.maxTolerance().toMillis() + "]: " + toleranceMs);
        }
        return tolerance;
    }

    int count(int count) {
        if (count <= 0 || count > limits.maxCount()) {
            throw ApiException.badRequest("count must be in [1, " + limits.maxCount() + "]: " + count);
        }
        return count;
    }

    private static String abbreviate(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }
}
