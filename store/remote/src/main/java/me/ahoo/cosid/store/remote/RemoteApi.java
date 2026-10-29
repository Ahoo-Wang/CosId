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

package me.ahoo.cosid.store.remote;

/**
 * The HTTP API (no version prefix, D30) of {@code cosid-server} (08-remote.md §2): the paths, the error codes of the error body
 * {@code {code, message}} and the field names both sides use. Keys and namespaces travel in request bodies, never in
 * paths.
 */
public final class RemoteApi {
    /** {@code POST {key, size, initialOffset}} → {@code {start, end}}; sealed: 410. */
    public static final String RESERVE = "/ranges:reserve";
    /** {@code POST {key}} → 204; 501 when the backing store cannot seal. */
    public static final String SEAL = "/ranges:seal";
    /**
     * {@code POST {namespace, ownerKey, incarnation, limit, ttlMs, toleranceMs, slotOffset?, preferredSlot?}} → the lease
     * and {@code reason}, {@code duplicateOwnerKey}; exhausted: 409. {@code slotOffset} (0 by default) is recorded with
     * the namespace.
     */
    public static final String ACQUIRE = "/slots:acquire";
    /**
     * {@code POST {namespace, slot, ownerKey, incarnation, fence, expiresAt?, floor?, ceiling?, storeNow?, ttlMs,
     * toleranceMs}} → the lease; lost: 409. The window fields are those of the lease, sent back as they were.
     */
    public static final String RENEW = "/slots:renew";
    /** {@code POST {namespace, slot, ownerKey, incarnation, fence, expiresAt?, floor?, ceiling?, storeNow?, lastIssued}} → 204; lost: 409. */
    public static final String RELEASE = "/slots:release";
    /** {@code GET ?count=n} → {@code {ids: [...]}}, from the generator named {@code default}; {@code /{generator}} names another. */
    public static final String IDS = "/ids";

    /** 400: a parameter is missing or out of its limits. */
    public static final String BAD_REQUEST = "bad_request";
    /** 401: no token, or an unknown one. */
    public static final String UNAUTHORIZED = "unauthorized";
    /** 403: the token may not use that namespace or key. */
    public static final String FORBIDDEN = "forbidden";
    /** 404: no such path or generator. */
    public static final String NOT_FOUND = "not_found";
    /** 405: the path takes another method. */
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    /** 409: every slot below the limit is held; the body also carries {@code earliestExpiresAt}. */
    public static final String SLOTS_EXHAUSTED = "slots_exhausted";
    /** 409: the owner or fence of the lease no longer match (renew, release). */
    public static final String LEASE_LOST = "lease_lost";
    /** 409: the namespace was first used with another limit, tolerance or slot offset. */
    public static final String NAMESPACE_MISMATCH = "namespace_mismatch";
    /** 409: the generator is disabled. */
    public static final String GENERATOR_DISABLED = "generator_disabled";
    /** 410: the range key is sealed. */
    public static final String RANGE_SEALED = "range_sealed";
    /** 422: the range would pass {@code Long.MAX_VALUE}. */
    public static final String RANGE_EXHAUSTED = "range_exhausted";
    /** 429: the client already uses as many range keys in the namespace as the server allows. */
    public static final String TOO_MANY_KEYS = "too_many_keys";
    /** 429: the client already uses range keys in as many namespaces as the server allows. */
    public static final String TOO_MANY_NAMESPACES = "too_many_namespaces";
    /** 500: the backing store failed. */
    public static final String STORE_ERROR = "store_error";
    /** 501: the backing store cannot seal keys. */
    public static final String SEAL_UNSUPPORTED = "seal_unsupported";
    /** 501: the backing store lacks the primitive. */
    public static final String UNSUPPORTED = "unsupported";
    /** 504: the backing store timed out; the client reports a {@link me.ahoo.cosid.store.StoreTimeoutException} cause. */
    public static final String STORE_TIMEOUT = "store_timeout";
    /** 503: the blocking pool is full; the response carries {@code Retry-After: 1}. */
    public static final String BUSY = "busy";

    private RemoteApi() {
    }
}
