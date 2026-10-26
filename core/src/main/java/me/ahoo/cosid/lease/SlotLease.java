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

package me.ahoo.cosid.lease;

import java.util.Objects;

/**
 * A lease of a time window {@code (floor, ceiling]} on a slot (05-machine-lease.md §2). Times are store-clock
 * milliseconds since the Unix epoch.
 *
 * @param namespace the namespace
 * @param slot      the slot, in {@code [0, limit)}
 * @param owner     the holder
 * @param fence     raised on every ownership change (acquire, takeover, release); unchanged by renewals
 * @param expiresAt when others may take the slot over
 * @param floor     the holder issues timestamps strictly above it
 * @param ceiling   the holder issues timestamps up to it, inclusive
 * @param storeNow  the store clock of the write that produced this lease
 */
public record SlotLease(String namespace, int slot, Owner owner,
                        long fence,
                        long expiresAt,
                        long floor,
                        long ceiling,
                        long storeNow) {
    public SlotLease {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(owner, "owner");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative: " + slot);
        }
        if (ceiling < floor) {
            throw new IllegalArgumentException("ceiling " + ceiling + " must not be below floor " + floor);
        }
    }
}
