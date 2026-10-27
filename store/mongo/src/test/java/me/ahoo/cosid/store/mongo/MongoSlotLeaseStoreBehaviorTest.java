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

package me.ahoo.cosid.store.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What the slot lease contract does not pin down for MongoDB: the slot choice priority in one namespace, the
 * provisioned documents and their unique index, owner keys that look like field paths, and the fence in the CAS.
 */
class MongoSlotLeaseStoreBehaviorTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    private final MongoSlotLeaseStore store = new MongoSlotLeaseStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT);
    private final MongoCollection<Document> slots = MongoTestSupport.database().getCollection(MongoSlotLeaseStore.COLLECTION);

    private static String newNamespace() {
        return "mongo-" + UUID.randomUUID();
    }

    private static Owner owner(String key) {
        return new Owner(key, UUID.randomUUID());
    }

    private AcquireResult.Acquired acquire(String namespace, Owner owner, int limit, OptionalInt preferred) {
        return assertInstanceOf(AcquireResult.Acquired.class, store.acquire(namespace, owner, limit, TTL, TOLERANCE, preferred));
    }

    @Test
    void selectionFollowsIdempotentPreferredStickyLowest() {
        String namespace = newNamespace();
        Owner a = owner("host-a");
        SlotLease first = acquire(namespace, a, 8, OptionalInt.empty()).lease();
        assertEquals(0, first.slot());
        SlotLease b = acquire(namespace, owner("host-b"), 8, OptionalInt.empty()).lease();
        SlotLease k = acquire(namespace, owner("host-k"), 8, OptionalInt.of(6)).lease();
        assertEquals(6, k.slot());
        assertTrue(store.release(b, b.ceiling()));
        assertTrue(store.release(k, k.ceiling()));

        AcquireResult.Acquired idempotent = acquire(namespace, a, 8, OptionalInt.of(3));
        assertEquals(AcquireReason.IDEMPOTENT, idempotent.reason(), "a held slot wins over a free preferred one");
        assertEquals(first.slot(), idempotent.lease().slot());
        assertEquals(first.fence(), idempotent.lease().fence());

        AcquireResult.Acquired preferred = acquire(namespace, owner("host-k"), 8, OptionalInt.of(3));
        assertEquals(AcquireReason.PREFERRED, preferred.reason(), "a free preferred slot wins over the sticky one");
        assertEquals(3, preferred.lease().slot());

        AcquireResult.Acquired sticky = acquire(namespace, owner("host-k"), 8, OptionalInt.of(3));
        assertEquals(AcquireReason.STICKY, sticky.reason(), "a held preferred slot falls back to the sticky one");
        assertEquals(6, sticky.lease().slot(), "though slot " + b.slot() + " is lower");

        AcquireResult.Acquired lowest = acquire(namespace, owner("host-z"), 8, OptionalInt.of(99));
        assertEquals(AcquireReason.LOWEST, lowest.reason(), "a preferred slot beyond the limit is ignored");
        assertEquals(b.slot(), lowest.lease().slot());
    }

    @Test
    void firstUseProvisionsEverySlotOnce() {
        String namespace = newNamespace();
        acquire(namespace, owner("host-a"), 16, OptionalInt.empty());
        MongoSlotLeaseStore other = new MongoSlotLeaseStore(MongoTestSupport.database(), MongoTestSupport.TIMEOUT);
        assertInstanceOf(AcquireResult.Acquired.class, other.acquire(namespace, owner("host-b"), 16, TTL, TOLERANCE, OptionalInt.empty()));
        List<Integer> provisioned = new ArrayList<>();
        slots.find(Filters.eq(MongoSlotLeaseStore.NS, namespace)).sort(Sorts.ascending(MongoSlotLeaseStore.SLOT))
            .forEach(slot -> provisioned.add(slot.getInteger(MongoSlotLeaseStore.SLOT)));
        assertEquals(16, provisioned.size());
        assertEquals(0, provisioned.get(0));
        assertEquals(15, provisioned.get(15));
        assertThrows(com.mongodb.MongoWriteException.class,
            () -> slots.insertOne(new Document(MongoSlotLeaseStore.NS, namespace).append(MongoSlotLeaseStore.SLOT, 3)),
            "(ns, slot) is unique");
    }

    @Test
    void ownerKeysThatLookLikeFieldPathsStayStrings() {
        String namespace = newNamespace();
        Owner dollar = owner("$fence");
        SlotLease lease = acquire(namespace, dollar, 2, OptionalInt.empty()).lease();
        Document stored = slots.find(Filters.and(Filters.eq(MongoSlotLeaseStore.NS, namespace), Filters.eq(MongoSlotLeaseStore.SLOT, lease.slot()))).first();
        assertEquals("$fence", stored.getString(MongoSlotLeaseStore.OWNER_KEY));
        assertEquals("$fence", stored.getString(MongoSlotLeaseStore.LAST_OWNER_KEY));
        assertEquals(AcquireReason.IDEMPOTENT, acquire(namespace, dollar, 2, OptionalInt.empty()).reason());
    }

    @Test
    void renewAndReleaseMatchTheFence() {
        String namespace = newNamespace();
        SlotLease lease = acquire(namespace, owner("host-a"), 1, OptionalInt.empty()).lease();
        SlotLease staleFence = new SlotLease(namespace, lease.slot(), lease.owner(), lease.fence() - 1, lease.expiresAt(),
            lease.floor(), lease.ceiling(), lease.storeNow());
        assertTrue(store.renew(staleFence, TTL, TOLERANCE).isEmpty());
        assertFalse(store.release(staleFence, lease.ceiling()));
        assertTrue(store.release(lease, lease.ceiling()));
        Document released = slots.find(Filters.eq(MongoSlotLeaseStore.NS, namespace)).first();
        assertNull(released.get(MongoSlotLeaseStore.INCARNATION));
        assertNull(released.get(MongoSlotLeaseStore.OWNER_KEY));
        assertEquals(lease.fence() + 1, released.getLong(MongoSlotLeaseStore.FENCE));
        assertEquals(released.getLong(MongoSlotLeaseStore.WRITTEN_AT), released.getLong(MongoSlotLeaseStore.EXPIRES_AT));
        assertEquals(lease.ceiling(), released.getLong(MongoSlotLeaseStore.NEXT_FLOOR));
    }

    @Test
    void rejectsInvalidArguments() {
        String namespace = newNamespace();
        Owner owner = owner("host-a");
        assertThrows(IllegalArgumentException.class, () -> store.acquire(namespace, owner, 0, TTL, TOLERANCE, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.acquire(namespace, owner, 1, Duration.ZERO, TOLERANCE, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> store.acquire(namespace, owner, 1, TTL, Duration.ofMillis(-1), OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> new MongoSlotLeaseStore(MongoTestSupport.database(), Duration.ofMillis(-1)));
    }
}
