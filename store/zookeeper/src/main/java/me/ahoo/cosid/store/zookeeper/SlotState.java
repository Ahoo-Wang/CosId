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

package me.ahoo.cosid.store.zookeeper;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The data of a slot node {@code /cosid/slot/{ns}/{slot}} (06-stores.md §2.4), as {@code name=value} lines.
 *
 * <p>The node keeps the fence in its data, never in {@code Stat.version}: the version only serves the compare-and-set,
 * so a node recreated with its data does not move the fence back. The times of a write come from the {@code mtime} of
 * that very write, which the client learns only from its response; so the node keeps the {@code ttl} and the lower
 * bounds {@code expiresMin} and {@code ceilingMin} carried over from the previous write, and the lease is
 * {@code expiresAt = max(expiresMin, mtime + ttl)}, {@code ceiling = max(ceilingMin, expiresAt + tolerance)}. Every
 * write reads the node at the version it then writes, so its bounds are the expiry and ceiling of the write before:
 * both never decrease while the slot is held, whatever the clock of the server that stamped the write (05 §3).
 *
 * @param ownerKey     the key of the holder; null when the slot is free
 * @param incarnation  the incarnation of the holder; null when the slot is free
 * @param lastOwnerKey the key of the last holder, for sticky choice
 * @param fence        raised on every ownership change
 * @param ttlMillis    the ttl of the last write while held
 * @param toleranceMillis the tolerance of the last write while held
 * @param floor        the floor of the current or last tenure
 * @param expiresMin   the expiry before the last write while held
 * @param ceilingMin   the ceiling before the last write while held, at least the floor
 * @param nextFloor    the floor of the next holder, once released
 */
record SlotState(String ownerKey, UUID incarnation, String lastOwnerKey, long fence, long ttlMillis, long toleranceMillis,
                 long floor, long expiresMin, long ceilingMin, long nextFloor) {
    /** A slot whose node does not exist yet. */
    static final SlotState NEW = new SlotState(null, null, null, 0, 0, 0, 0, 0, 0, 0);
    private static final byte[] OWNER = Ascii.bytes("owner");
    private static final byte[] INCARNATION = Ascii.bytes("incarnation");
    private static final byte[] LAST_OWNER = Ascii.bytes("lastOwner");
    /** The numeric fields, in the order of the record. */
    private static final byte[][] NUMBERS = {Ascii.bytes("fence"), Ascii.bytes("ttl"), Ascii.bytes("tolerance"), Ascii.bytes("floor"),
        Ascii.bytes("expiresMin"), Ascii.bytes("ceilingMin"), Ascii.bytes("nextFloor")};

    boolean held() {
        return incarnation != null;
    }

    Owner owner() {
        return held() ? new Owner(ownerKey, incarnation) : null;
    }

    /**
     * When the lease expires; for a free slot the time it was released.
     *
     * @param mtime the modification time of the node
     * @return the expiry
     */
    long expiresAt(long mtime) {
        return Math.max(expiresMin, mtime + ttlMillis);
    }

    long ceiling(long mtime) {
        return Math.max(ceilingMin, expiresAt(mtime) + toleranceMillis);
    }

    /**
     * The floor of the next holder: the ceiling while held (05 §2.2), the exact value of the release once free.
     */
    long nextFloor(long mtime) {
        return held() ? ceiling(mtime) : nextFloor;
    }

    /** Free: nobody holds it, or the lease expired before {@code now} (05 §2.1). */
    boolean free(long mtime, long now) {
        return !held() || expiresAt(mtime) < now;
    }

    boolean heldBy(SlotLease lease) {
        return held() && incarnation.equals(lease.owner().incarnation()) && fence == lease.fence();
    }

    SlotLease toLease(String namespace, int slot, long mtime, long storeNow) {
        return new SlotLease(namespace, slot, owner(), fence, expiresAt(mtime), floor, ceiling(mtime), storeNow);
    }

    /** The state after {@code owner} takes the slot: a new tenure starting at the next floor. */
    SlotState take(Owner owner, long mtime, long ttl, long tolerance) {
        long start = nextFloor(mtime);
        return new SlotState(owner.key(), owner.incarnation(), owner.key(), fence + 1, ttl, tolerance, start, 0, start, 0);
    }

    /** The state after a renewal: the bounds become the expiry and ceiling as they were. */
    SlotState renew(long mtime, long ttl, long tolerance) {
        return new SlotState(ownerKey, incarnation, lastOwnerKey, fence, ttl, tolerance, floor, expiresAt(mtime), ceiling(mtime), 0);
    }

    /** The state after a release: free, the next floor at {@code max(floor, min(lastIssued, ceiling))}. */
    SlotState release(long mtime, long lastIssued) {
        long ceiling = ceiling(mtime);
        return new SlotState(null, null, lastOwnerKey, fence + 1, 0, 0, floor, 0, ceiling, Math.max(floor, Math.min(lastIssued, ceiling)));
    }

    byte[] bytes() {
        Ascii.Writer data = new Ascii.Writer(192);
        if (held()) {
            data.raw(OWNER).raw('=').text(ownerKey).raw('\n');
            data.raw(INCARNATION).raw('=').uuid(incarnation).raw('\n');
        }
        if (lastOwnerKey != null) {
            data.raw(LAST_OWNER).raw('=').text(lastOwnerKey).raw('\n');
        }
        long[] numbers = {fence, ttlMillis, toleranceMillis, floor, expiresMin, ceilingMin, nextFloor};
        for (int i = 0; i < NUMBERS.length; i++) {
            data.raw(NUMBERS[i]).raw('=').number(numbers[i]).raw('\n');
        }
        return data.toBytes();
    }

    static SlotState parse(String path, byte[] data) {
        if (data == null || data.length == 0) {
            return NEW;
        }
        String owner = null;
        UUID incarnation = null;
        String lastOwner = null;
        long[] numbers = new long[NUMBERS.length];
        int found = 0;
        try {
            for (int start = 0; start < data.length; ) {
                int end = start;
                int eq = -1;
                while (end < data.length && data[end] != '\n') {
                    if (eq < 0 && data[end] == '=') {
                        eq = end;
                    }
                    end++;
                }
                if (eq > start) {
                    if (Ascii.is(data, start, eq, OWNER)) {
                        owner = Ascii.text(data, eq + 1, end);
                    } else if (Ascii.is(data, start, eq, INCARNATION)) {
                        incarnation = Ascii.uuid(data, eq + 1, end);
                    } else if (Ascii.is(data, start, eq, LAST_OWNER)) {
                        lastOwner = Ascii.text(data, eq + 1, end);
                    } else {
                        for (int i = 0; i < NUMBERS.length; i++) {
                            if (Ascii.is(data, start, eq, NUMBERS[i])) {
                                numbers[i] = Ascii.number(data, eq + 1, end);
                                found |= 1 << i;
                                break;
                            }
                        }
                    }
                }
                start = end + 1;
            }
            if (found != (1 << NUMBERS.length) - 1) {
                throw new IllegalArgumentException("missing fields in " + new String(data, StandardCharsets.UTF_8));
            }
            return new SlotState(owner, incarnation, lastOwner, numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], numbers[6]);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new CosIdException("Slot node " + path + " is unreadable: " + e.getMessage(), e);
        }
    }
}
