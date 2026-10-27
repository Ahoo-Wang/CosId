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

package me.ahoo.cosid.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The machine slot of a runtime (D18, 05-machine-lease.md).
 */
public sealed interface MachineSpec {
    int bits();

    /**
     * A slot leased from a store.
     *
     * @param store         the store name
     * @param bits          the slots are {@code [0, 2^bits)}; default 10
     * @param ttl           the lease time to live; default 30s
     * @param tolerance     the ceiling lies this far beyond the expiry (D20); default 1s
     * @param preferredSlot the slot to try first, such as a StatefulSet ordinal
     * @param ownerKey      the owner key; derived as in 07 §1.1 when empty
     */
    record Leased(String store, int bits, Duration ttl, Duration tolerance, OptionalInt preferredSlot,
                  Optional<String> ownerKey) implements MachineSpec {
        public Leased {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(ttl, "ttl");
            Objects.requireNonNull(tolerance, "tolerance");
            Objects.requireNonNull(preferredSlot, "preferredSlot");
            Objects.requireNonNull(ownerKey, "ownerKey");
        }
    }

    /**
     * A slot the user keeps unique, with a local window file.
     *
     * @param bits      the slot bits
     * @param slot      the slot
     * @param stateFile the window file; none means no clock regression protection
     * @param ahead     how far ahead the ceiling is written; default 10s
     * @param notBefore the floor when the file does not exist yet (given by the migration tool)
     */
    record Static(int bits, int slot, Optional<Path> stateFile, Duration ahead,
                  Optional<Instant> notBefore) implements MachineSpec {
        public Static {
            Objects.requireNonNull(stateFile, "stateFile");
            Objects.requireNonNull(ahead, "ahead");
            Objects.requireNonNull(notBefore, "notBefore");
        }
    }
}
