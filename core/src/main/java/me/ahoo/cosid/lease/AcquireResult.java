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

/**
 * The outcome of {@link SlotLeaseStore#acquire}.
 */
public sealed interface AcquireResult {
    /**
     * A slot was acquired.
     *
     * @param lease             the lease
     * @param reason            which step of the slot choice gave the slot (§2.1)
     * @param duplicateOwnerKey whether another incarnation of the owner key holds a live lease in the namespace, as two
     *                          runtimes started with one identity would
     */
    record Acquired(SlotLease lease, AcquireReason reason, boolean duplicateOwnerKey) implements AcquireResult {
        public Acquired {
            java.util.Objects.requireNonNull(lease, "lease");
            java.util.Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Every slot below the limit is held.
     *
     * @param earliestExpiresAt the store-clock time the first live lease expires
     */
    record Exhausted(long earliestExpiresAt) implements AcquireResult {
    }
}
