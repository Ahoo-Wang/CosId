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

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.store.remote.RemoteApi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

/**
 * The two store primitives over HTTP (08-remote.md §2): each call is one call of the backing store, which runs the
 * slot choice and stamps the times. The checks run on the request thread, the store call on the bounded pool.
 */
@RestController
@Tag(name = "store", description = "The range and slot lease primitives of the backing store")
class StoreController {
    private final BackingStore store;
    private final Requests requests;
    private final KeyCap keyCap;
    private final BlockingCalls calls;

    StoreController(BackingStore store, Requests requests, KeyCap keyCap, BlockingCalls calls) {
        this.store = store;
        this.requests = requests;
        this.keyCap = keyCap;
        this.calls = calls;
    }

    @PostMapping(RemoteApi.RESERVE)
    @Operation(summary = "Reserves [start, end) of a range key; 410 when the key is sealed")
    CompletableFuture<Bodies.RangeBody> reserve(@RequestBody Bodies.ReserveRequest request, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        String key = requests.key(request.key());
        long size = requests.size(request.size());
        long initialOffset = Requests.notNegative(request.initialOffset(), "initialOffset");
        allowKey(grant, key);
        Runnable forget = keyCap.admit(grant, key);
        CompletableFuture<Bodies.RangeBody> reserved;
        try {
            reserved = calls.submit(() -> Bodies.RangeBody.of(store.ranges().reserve(key, size, initialOffset)));
        } catch (RuntimeException e) {
            forget.run();
            throw e;
        }
        // Only keys the store reserved stay counted (KeyCap).
        return reserved.whenComplete((range, error) -> {
            if (error != null) {
                forget.run();
            }
        });
    }

    @PostMapping(RemoteApi.SEAL)
    @Operation(summary = "Seals a range key: 204, or 501 when the backing store cannot seal")
    CompletableFuture<ResponseEntity<Object>> seal(@RequestBody Bodies.SealRequest request, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        String key = requests.key(request.key());
        allowKey(grant, key);
        return calls.submit(() -> {
            if (store.ranges().seal(key)) {
                return ResponseEntity.noContent().build();
            }
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, RemoteApi.SEAL_UNSUPPORTED, "The backing store cannot seal range keys");
        });
    }

    @PostMapping(RemoteApi.ACQUIRE)
    @Operation(summary = "Acquires a slot lease (05 §2.1); 409 slots_exhausted with earliestExpiresAt when every slot is held")
    CompletableFuture<ResponseEntity<Object>> acquire(@RequestBody Bodies.AcquireRequest request, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        String namespace = requests.namespace(request.namespace());
        Owner owner = new Owner(requests.ownerKey(request.ownerKey()), Requests.incarnation(request.incarnation()));
        int limit = requests.limit(request.limit());
        Duration ttl = requests.ttl(request.ttlMs());
        Duration tolerance = requests.tolerance(request.toleranceMs());
        OptionalInt preferredSlot = OptionalInt.empty();
        if (request.preferredSlot() != null) {
            if (request.preferredSlot() < 0 || request.preferredSlot() >= limit) {
                throw ApiException.badRequest("preferredSlot must be in [0, limit): " + request.preferredSlot());
            }
            preferredSlot = OptionalInt.of(request.preferredSlot());
        }
        allowNamespace(grant, namespace);
        OptionalInt preferred = preferredSlot;
        return calls.submit(() -> {
            AcquireResult result = store.slots().acquire(namespace, owner, limit, ttl, tolerance, preferred);
            if (result instanceof AcquireResult.Acquired acquired) {
                return ResponseEntity.ok(Bodies.AcquiredBody.of(acquired));
            }
            long earliestExpiresAt = ((AcquireResult.Exhausted) result).earliestExpiresAt();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Bodies.ExhaustedBody(RemoteApi.SLOTS_EXHAUSTED,
                "Every slot of [" + namespace + "] below " + limit + " is held", earliestExpiresAt));
        });
    }

    @PostMapping(RemoteApi.RENEW)
    @Operation(summary = "Renews a slot lease; 409 lease_lost when the owner or fence no longer match")
    CompletableFuture<ResponseEntity<Object>> renew(@RequestBody Bodies.HeldRequest request, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        SlotLease lease = held(request);
        Duration ttl = requests.ttl(request.ttlMs());
        Duration tolerance = requests.tolerance(request.toleranceMs());
        allowNamespace(grant, lease.namespace());
        return calls.submit(() -> store.slots().renew(lease, ttl, tolerance)
            .<ResponseEntity<Object>>map(renewed -> ResponseEntity.ok(Bodies.LeaseBody.of(renewed)))
            .orElseGet(() -> lost(lease)));
    }

    @PostMapping(RemoteApi.RELEASE)
    @Operation(summary = "Releases a slot lease: 204, or 409 lease_lost when the owner or fence no longer match")
    CompletableFuture<ResponseEntity<Object>> release(@RequestBody Bodies.HeldRequest request, @RequestAttribute(Grant.ATTRIBUTE) Grant grant) {
        SlotLease lease = held(request);
        long lastIssued = Requests.required(request.lastIssued(), "lastIssued");
        allowNamespace(grant, lease.namespace());
        return calls.submit(() -> store.slots().release(lease, lastIssued) ? ResponseEntity.noContent().build() : lost(lease));
    }

    private SlotLease held(Bodies.HeldRequest request) {
        String namespace = requests.namespace(request.namespace());
        int slot = Requests.required(request.slot(), "slot");
        if (slot < 0) {
            throw ApiException.badRequest("slot must not be negative: " + slot);
        }
        Owner owner = new Owner(requests.ownerKey(request.ownerKey()), Requests.incarnation(request.incarnation()));
        long fence = Requests.required(request.fence(), "fence");
        long floor = request.floor() == null ? 0 : request.floor();
        long ceiling = request.ceiling() == null || request.ceiling() < floor ? floor : request.ceiling();
        return SlotLease.builder()
            .namespace(namespace)
            .slot(slot)
            .owner(owner)
            .fence(fence)
            .expiresAt(orZero(request.expiresAt()))
            .floor(floor)
            .ceiling(ceiling)
            .storeNow(orZero(request.storeNow()))
            .build();
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }

    private static ResponseEntity<Object> lost(SlotLease lease) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Bodies.ErrorBody(RemoteApi.LEASE_LOST,
            "Slot " + lease.slot() + " of [" + lease.namespace() + "] is no longer held by " + lease.owner().incarnation() + " at fence " + lease.fence()));
    }

    private static void allowKey(Grant grant, String key) {
        if (!grant.allowsKey(key)) {
            throw ApiException.forbidden("[" + grant.name() + "] may not use the range key [" + key + "]");
        }
    }

    private static void allowNamespace(Grant grant, String namespace) {
        if (!grant.allowsNamespace(namespace)) {
            throw ApiException.forbidden("[" + grant.name() + "] may not use the namespace [" + namespace + "]");
        }
    }
}
