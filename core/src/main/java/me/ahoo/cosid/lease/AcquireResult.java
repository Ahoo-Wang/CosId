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
 * The outcome of {@link SlotLeaseStore#acquire}. Closed: an acquisition either succeeds or finds every slot held,
 * and both store implementations and the core switch over the two variants.
 */
public sealed interface AcquireResult {
    /**
     * A slot was acquired. Immutable; built with {@link #builder(SlotLease, AcquireReason)}, the only way to create
     * one, so fields can be added with a default (api-freeze.md §4).
     */
    final class Acquired implements AcquireResult {
        private final SlotLease lease;
        private final AcquireReason reason;
        private final boolean duplicateOwnerKey;

        private Acquired(Builder builder) {
            this.lease = builder.lease;
            this.reason = builder.reason;
            this.duplicateOwnerKey = builder.duplicateOwnerKey;
        }

        /**
         * A builder of an acquisition.
         *
         * @param lease  the lease
         * @param reason which step of the slot choice gave the slot (§2.1)
         * @return the builder
         */
        public static Builder builder(SlotLease lease, AcquireReason reason) {
            return new Builder(lease, reason);
        }

        /**
         * The lease.
         *
         * @return the lease
         */
        public SlotLease lease() {
            return lease;
        }

        /**
         * Which step of the slot choice gave the slot (§2.1).
         *
         * @return the reason
         */
        public AcquireReason reason() {
            return reason;
        }

        /**
         * Whether another incarnation of the owner key holds a live lease in the namespace, as two runtimes started
         * with one identity would.
         *
         * @return whether the owner key is held twice; default false
         */
        public boolean duplicateOwnerKey() {
            return duplicateOwnerKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Acquired that && lease.equals(that.lease) && reason == that.reason && duplicateOwnerKey == that.duplicateOwnerKey;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lease, reason, duplicateOwnerKey);
        }

        @Override
        public String toString() {
            return "Acquired[lease=" + lease + ", reason=" + reason + ", duplicateOwnerKey=" + duplicateOwnerKey + "]";
        }

        /**
         * Builds an {@link Acquired}.
         */
        public static final class Builder {
            private final SlotLease lease;
            private final AcquireReason reason;
            private boolean duplicateOwnerKey;

            private Builder(SlotLease lease, AcquireReason reason) {
                this.lease = Objects.requireNonNull(lease, "lease");
                this.reason = Objects.requireNonNull(reason, "reason");
            }

            /**
             * Sets whether another incarnation of the owner key holds a live lease in the namespace.
             *
             * @param duplicateOwnerKey whether the owner key is held twice; default false
             * @return this builder
             */
            public Builder duplicateOwnerKey(boolean duplicateOwnerKey) {
                this.duplicateOwnerKey = duplicateOwnerKey;
                return this;
            }

            /**
             * Builds the acquisition.
             *
             * @return the acquisition
             */
            public Acquired build() {
                return new Acquired(this);
            }
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
