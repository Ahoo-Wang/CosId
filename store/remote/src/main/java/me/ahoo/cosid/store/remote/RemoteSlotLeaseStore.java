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

import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@link SlotLeaseStore} over {@code POST /slots:*}: the server runs the slot choice (05 §2.1) on its backing store
 * and the backing store stamps the times, in one round trip. Leases travel flat:
 * {@code {namespace, slot, ownerKey, incarnation, fence, expiresAt, floor, ceiling, storeNow}}.
 */
final class RemoteSlotLeaseStore implements SlotLeaseStore {
    private final RemoteClient client;

    RemoteSlotLeaseStore(RemoteClient client) {
        this.client = client;
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, int slotOffset, OptionalInt preferredSlot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("namespace", namespace);
        body.put("ownerKey", owner.key());
        body.put("incarnation", owner.incarnation().toString());
        body.put("limit", limit);
        body.put("ttlMs", ttl.toMillis());
        body.put("toleranceMs", tolerance.toMillis());
        if (preferredSlot.isPresent()) {
            body.put("preferredSlot", preferredSlot.getAsInt());
        }
        RemoteClient.Response response = client.post(RemoteApi.ACQUIRE, body);
        if (response.status() == 200) {
            AcquireReason reason = AcquireReason.valueOf(response.textField("reason"));
            boolean duplicateOwnerKey = Boolean.TRUE.equals(response.body().get("duplicateOwnerKey"));
            return AcquireResult.Acquired.builder(lease(response), reason).duplicateOwnerKey(duplicateOwnerKey).build();
        }
        if (RemoteApi.SLOTS_EXHAUSTED.equals(response.code())) {
            return new AcquireResult.Exhausted(response.longField("earliestExpiresAt"));
        }
        throw failure(response, namespace, "acquire in [" + namespace + "]");
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Map<String, Object> body = held(lease);
        body.put("ttlMs", ttl.toMillis());
        body.put("toleranceMs", tolerance.toMillis());
        RemoteClient.Response response = client.post(RemoteApi.RENEW, body);
        if (response.status() == 200) {
            return Optional.of(lease(response));
        }
        if (RemoteApi.LEASE_LOST.equals(response.code())) {
            return Optional.empty();
        }
        throw failure(response, lease.namespace(), "renew of slot " + lease.slot() + " in [" + lease.namespace() + "]");
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Map<String, Object> body = held(lease);
        body.put("lastIssued", lastIssued);
        RemoteClient.Response response = client.post(RemoteApi.RELEASE, body);
        if (response.status() == 204 || response.status() == 200) {
            return true;
        }
        if (RemoteApi.LEASE_LOST.equals(response.code())) {
            return false;
        }
        throw failure(response, lease.namespace(), "release of slot " + lease.slot() + " in [" + lease.namespace() + "]");
    }

    /** The fields that name the holding; the window rides along for backing stores that look at it. */
    private static Map<String, Object> held(SlotLease lease) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("namespace", lease.namespace());
        body.put("slot", lease.slot());
        body.put("ownerKey", lease.owner().key());
        body.put("incarnation", lease.owner().incarnation().toString());
        body.put("fence", lease.fence());
        body.put("expiresAt", lease.expiresAt());
        body.put("floor", lease.floor());
        body.put("ceiling", lease.ceiling());
        body.put("storeNow", lease.storeNow());
        return body;
    }

    private static SlotLease lease(RemoteClient.Response response) {
        Owner owner = new Owner(response.textField("ownerKey"), UUID.fromString(response.textField("incarnation")));
        return SlotLease.builder()
            .namespace(response.textField("namespace"))
            .slot(Math.toIntExact(response.longField("slot")))
            .owner(owner)
            .fence(response.longField("fence"))
            .expiresAt(response.longField("expiresAt"))
            .floor(response.longField("floor"))
            .ceiling(response.longField("ceiling"))
            .storeNow(response.longField("storeNow"))
            .build();
    }

    private static RuntimeException failure(RemoteClient.Response response, String namespace, String call) {
        if (RemoteApi.NAMESPACE_MISMATCH.equals(response.code())) {
            Object message = response.body().get("message");
            return new NamespaceMismatchException(namespace, message == null ? "rejected by the server" : message.toString());
        }
        return response.error(call);
    }
}
