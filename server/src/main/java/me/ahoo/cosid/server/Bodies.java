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
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.segment.Range;

import java.util.List;

/**
 * The request and response bodies of the {@code /v5} API (08-remote.md §2). Fields are boxed so a missing one is a
 * {@code 400}, not a zero. Leases travel flat: the owner is {@code ownerKey} and {@code incarnation}.
 */
final class Bodies {
    private Bodies() {
    }

    /**
     * {@code POST /v5/ranges:reserve}.
     *
     * @param key           the range key
     * @param size          the size of the range
     * @param initialOffset where the first range of a new key starts
     */
    record ReserveRequest(String key, Long size, Long initialOffset) {
    }

    /**
     * {@code POST /v5/ranges:seal}.
     *
     * @param key the range key
     */
    record SealRequest(String key) {
    }

    /**
     * A reserved range {@code [start, end)}.
     *
     * @param start the first value
     * @param end   the end, exclusive
     */
    record RangeBody(long start, long end) {
        static RangeBody of(Range range) {
            return new RangeBody(range.start(), range.end());
        }
    }

    /**
     * {@code POST /v5/slots:acquire}.
     *
     * @param namespace     the namespace
     * @param ownerKey      the owner key
     * @param incarnation   the owner incarnation, a UUID
     * @param limit         the slots are {@code [0, limit)}
     * @param ttlMs         the lease ttl
     * @param toleranceMs   the tolerance
     * @param preferredSlot the slot to try first, optional
     */
    record AcquireRequest(String namespace, String ownerKey, String incarnation, Integer limit, Long ttlMs, Long toleranceMs,
                          Integer preferredSlot) {
    }

    /**
     * {@code POST /v5/slots:renew} and {@code /v5/slots:release}: the holding, and the ttl (renew) or the last issued
     * timestamp (release). {@code expiresAt}, {@code floor}, {@code ceiling} and {@code storeNow} of the lease are
     * optional.
     *
     * @param namespace   the namespace
     * @param slot        the slot
     * @param ownerKey    the owner key
     * @param incarnation the owner incarnation
     * @param fence       the fence
     * @param expiresAt   the lease's expiry, optional
     * @param floor       the lease's floor, optional
     * @param ceiling     the lease's ceiling, optional
     * @param storeNow    the lease's store time, optional
     * @param ttlMs       the ttl (renew)
     * @param toleranceMs the tolerance (renew)
     * @param lastIssued  the largest timestamp issued (release)
     */
    record HeldRequest(String namespace, Integer slot, String ownerKey, String incarnation, Long fence,
                       Long expiresAt, Long floor, Long ceiling, Long storeNow,
                       Long ttlMs, Long toleranceMs, Long lastIssued) {
    }

    /**
     * A lease.
     *
     * @param namespace   the namespace
     * @param slot        the slot
     * @param ownerKey    the owner key
     * @param incarnation the owner incarnation
     * @param fence       the fence
     * @param expiresAt   when others may take the slot over, store clock
     * @param floor       the holder issues timestamps above it
     * @param ceiling     the holder issues timestamps up to it
     * @param storeNow    the store clock of the write
     */
    record LeaseBody(String namespace, int slot, String ownerKey, String incarnation, long fence, long expiresAt, long floor, long ceiling,
                     long storeNow) {
        static LeaseBody of(SlotLease lease) {
            return new LeaseBody(lease.namespace(), lease.slot(), lease.owner().key(), lease.owner().incarnation().toString(), lease.fence(),
                lease.expiresAt(), lease.floor(), lease.ceiling(), lease.storeNow());
        }
    }

    /**
     * An acquired lease, with the step of the slot choice that gave it.
     *
     * @param namespace         the namespace
     * @param slot              the slot
     * @param ownerKey          the owner key
     * @param incarnation       the owner incarnation
     * @param fence             the fence
     * @param expiresAt         when others may take the slot over
     * @param floor             the floor
     * @param ceiling           the ceiling
     * @param storeNow          the store clock of the write
     * @param reason            {@code IDEMPOTENT}, {@code PREFERRED}, {@code STICKY} or {@code LOWEST}
     * @param duplicateOwnerKey whether another incarnation of the owner key holds a live lease
     */
    record AcquiredBody(String namespace, int slot, String ownerKey, String incarnation, long fence, long expiresAt, long floor, long ceiling,
                        long storeNow, String reason, boolean duplicateOwnerKey) {
        static AcquiredBody of(AcquireResult.Acquired acquired) {
            SlotLease lease = acquired.lease();
            return new AcquiredBody(lease.namespace(), lease.slot(), lease.owner().key(), lease.owner().incarnation().toString(), lease.fence(),
                lease.expiresAt(), lease.floor(), lease.ceiling(), lease.storeNow(), acquired.reason().name(), acquired.duplicateOwnerKey());
        }
    }

    /**
     * The error body.
     *
     * @param code    the code ({@link me.ahoo.cosid.store.remote.RemoteApi})
     * @param message what went wrong
     */
    record ErrorBody(String code, String message) {
    }

    /**
     * {@code 409 slots_exhausted}: every slot is held.
     *
     * @param code              {@code slots_exhausted}
     * @param message           what went wrong
     * @param earliestExpiresAt the store time the first live lease expires
     */
    record ExhaustedBody(String code, String message, long earliestExpiresAt) {
    }

    /**
     * {@code GET /v5/ids}.
     *
     * @param ids the ids
     */
    record IdsBody(List<Long> ids) {
    }

    /**
     * {@code GET /v5/ids/strings}.
     *
     * @param ids the ids, encoded by the generator's codec
     */
    record StringIdsBody(List<String> ids) {
    }

    /**
     * {@code GET /v5/ids/{generator}/periodic}.
     *
     * @param period the period every value belongs to
     * @param values the values
     */
    record PeriodicBody(String period, List<Long> values) {
    }
}
