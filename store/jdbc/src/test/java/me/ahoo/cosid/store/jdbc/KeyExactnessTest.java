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

package me.ahoo.cosid.store.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Keys are exact on every dialect (rc-review B-M1): range keys, namespaces and owner keys that differ only in case or
 * in a trailing space are different keys, as on the other backends, never one row that seals, offsets or sticks for
 * both. SQL Server compares strings ignoring trailing spaces under every collation, so there a key ending with a
 * space is rejected instead ({@link #rejectsTrailingSpaces()}).
 */
abstract class KeyExactnessTest {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    abstract TestDatabase database();

    /**
     * Whether the dialect rejects keys that end with a space, as SQL Server does.
     */
    boolean rejectsTrailingSpaces() {
        return false;
    }

    @BeforeEach
    void available() {
        database().assumeAvailable();
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }

    private static Owner owner(String key) {
        return new Owner(key, UUID.randomUUID());
    }

    private static SlotLease acquired(SlotLeaseStore store, String namespace, Owner owner, int limit) {
        return assertInstanceOf(AcquireResult.Acquired.class, store.acquire(namespace, owner, limit, TTL, TOLERANCE, OptionalInt.empty())).lease();
    }

    @Test
    void rangeKeysDifferingInCaseAreDifferentKeys() {
        String id = unique();
        String upper = "tck/exact/Order-" + id;
        String lower = "tck/exact/order-" + id;
        RangeStore store = database().store().ranges();
        assertEquals(new Range(100, 110), store.reserve(upper, 10, 100));
        assertEquals(new Range(5, 15), store.reserve(lower, 10, 5), "the lower-case key starts at its own offset");
        assertTrue(store.seal(upper));
        assertThrows(RangeSealedException.class, () -> store.reserve(upper, 1, 0));
        assertEquals(new Range(15, 16), store.reserve(lower, 1, 5), "sealing one key leaves the other open");
    }

    @Test
    void rangeKeysDifferingInATrailingSpaceAreDifferentKeys() {
        String key = "tck/exact/space-" + unique();
        RangeStore store = database().store().ranges();
        assertEquals(new Range(100, 110), store.reserve(key, 10, 100));
        if (rejectsTrailingSpaces()) {
            assertThrows(IllegalArgumentException.class, () -> store.reserve(key + " ", 10, 5));
            assertThrows(IllegalArgumentException.class, () -> store.seal(key + " "));
            assertEquals(new Range(110, 111), store.reserve(key, 1, 100), "the rejected key left the key alone");
            return;
        }
        assertEquals(new Range(5, 15), store.reserve(key + " ", 10, 5), "the key with a trailing space starts at its own offset");
        assertTrue(store.seal(key + " "));
        assertEquals(new Range(110, 111), store.reserve(key, 1, 100), "sealing one key leaves the other open");
    }

    @Test
    void namespacesDifferingInCaseAreDifferentNamespaces() {
        String id = unique();
        SlotLeaseStore store = database().store().slots();
        assertEquals(0, acquired(store, "Exact-" + id, owner("host-a"), 1).slot());
        assertEquals(0, acquired(store, "exact-" + id, owner("host-b"), 2).slot(), "a namespace of its own: another limit, a free slot 0");
    }

    @Test
    void namespacesDifferingInATrailingSpaceAreDifferentNamespaces() {
        String namespace = "exact-" + unique();
        SlotLeaseStore store = database().store().slots();
        assertEquals(0, acquired(store, namespace, owner("host-a"), 1).slot());
        if (rejectsTrailingSpaces()) {
            assertThrows(IllegalArgumentException.class,
                () -> store.acquire(namespace + " ", owner("host-b"), 2, TTL, TOLERANCE, OptionalInt.empty()));
            return;
        }
        assertEquals(0, acquired(store, namespace + " ", owner("host-b"), 2).slot());
    }

    @Test
    void ownerKeysDifferingInCaseAreDifferentOwners() {
        assertOwnerKeysApart("Host-" + unique(), true);
    }

    @Test
    void ownerKeysDifferingInATrailingSpaceAreDifferentOwners() {
        assertOwnerKeysApart("host-" + unique(), false);
    }

    /**
     * {@code other} (the key in another case, or with a trailing space) is neither sticky to the slot {@code key} held
     * last nor flagged as a duplicate of its live lease.
     */
    private void assertOwnerKeysApart(String key, boolean byCase) {
        String other = byCase ? key.toLowerCase(java.util.Locale.ROOT) : key + " ";
        String namespace = "exact-" + unique();
        SlotLeaseStore store = database().store().slots();
        SlotLease first = acquired(store, namespace, owner("first"), 4);
        SlotLease held = acquired(store, namespace, owner(key), 4);
        assertEquals(1, held.slot());
        if (!byCase && rejectsTrailingSpaces()) {
            assertThrows(IllegalArgumentException.class, () -> store.acquire(namespace, owner(other), 4, TTL, TOLERANCE, OptionalInt.empty()));
            return;
        }
        AcquireResult.Acquired live = assertInstanceOf(AcquireResult.Acquired.class,
            store.acquire(namespace, owner(other), 4, TTL, TOLERANCE, OptionalInt.empty()));
        assertFalse(live.duplicateOwnerKey(), "another key is no duplicate of the live lease");
        assertTrue(store.release(live.lease(), live.lease().ceiling()));
        assertTrue(store.release(first, first.ceiling()));
        assertTrue(store.release(held, held.ceiling()));
        AcquireResult.Acquired next = assertInstanceOf(AcquireResult.Acquired.class,
            store.acquire(namespace, owner(other), 4, TTL, TOLERANCE, OptionalInt.empty()));
        assertEquals(AcquireReason.STICKY, next.reason(), "sticky to its own last slot");
        assertEquals(live.lease().slot(), next.lease().slot(), "not to slot " + held.slot() + " of " + key);
    }
}
