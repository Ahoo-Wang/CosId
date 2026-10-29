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
 *
 * <p>The variants are the current snapshot of a closed set of specs: a minor version may add a variant, so keep a
 * {@code default} branch in a {@code switch} over them (D40). The variants are immutable and built with their
 * builders, the only way to create one, so fields can be added with a default (api-freeze.md §4).
 */
public sealed interface MachineSpec {
    /**
     * The slot bits: the slots are {@code [0, 2^bits)}.
     *
     * @return the slot bits
     */
    int bits();

    /**
     * The namespace of the slot leases (D37): a machine shared by several services, such as the slot of a cosid80
     * generator unique across them, names its own; every machine of a runtime has another namespace.
     *
     * @return the namespace; empty for the {@link RuntimeSpec#namespace()}
     */
    Optional<String> namespace();

    /**
     * A slot leased from a store.
     */
    final class Leased implements MachineSpec {
        private final String store;
        private final int bits;
        private final Duration ttl;
        private final Duration tolerance;
        private final Duration acquireTimeout;
        private final int slotOffset;
        private final OptionalInt preferredSlot;
        private final Optional<String> ownerKey;
        private final Optional<String> namespace;

        private Leased(Builder builder) {
            this.store = builder.store;
            this.bits = builder.bits;
            this.ttl = builder.ttl;
            this.tolerance = builder.tolerance;
            this.acquireTimeout = builder.acquireTimeout == null ? builder.ttl : builder.acquireTimeout;
            this.slotOffset = builder.slotOffset;
            this.namespace = Optional.ofNullable(builder.namespace);
            this.preferredSlot = builder.preferredSlot;
            this.ownerKey = Optional.ofNullable(builder.ownerKey);
        }

        /**
         * A builder of a leased slot.
         *
         * @param store the store name
         * @return the builder
         */
        public static Builder builder(String store) {
            return new Builder(store);
        }

        /**
         * The store name.
         *
         * @return the store name
         */
        public String store() {
            return store;
        }

        /**
         * The slot bits: the slots are {@code [0, 2^bits)}.
         *
         * @return the slot bits; default 10
         */
        @Override
        public int bits() {
            return bits;
        }

        /**
         * The lease time to live.
         *
         * @return the ttl; default 30s
         */
        public Duration ttl() {
            return ttl;
        }

        /**
         * How far beyond the expiry the ceiling lies (D20).
         *
         * @return the tolerance; default 1s
         */
        public Duration tolerance() {
            return tolerance;
        }

        /**
         * How long {@code CosIdRuntime.start} waits for a slot while every slot is held (D42): it retries as leases
         * expire, then fails; {@link Duration#ZERO} tries once. Align it with the startup probe of a rolling update.
         *
         * @return the acquire timeout, not negative; default the {@link #ttl()}
         */
        public Duration acquireTimeout() {
            return acquireTimeout;
        }

        /**
         * Added to the slot to give the node of the ids (api-freeze.md §2.1.1): the nodes of this machine are
         * {@code [slotOffset, slotOffset + 2^bits)}, apart from the 4.x machine ids below it during a rolling
         * migration. The store keeps the slots {@code [0, 2^bits)} and records the offset; every client of the namespace
         * must use the same one, and once used it never changes.
         *
         * @return the slot offset, not negative; default 0
         */
        public int slotOffset() {
            return slotOffset;
        }

        /**
         * The slot to try first, such as a StatefulSet ordinal.
         *
         * @return the preferred slot; default none
         */
        public OptionalInt preferredSlot() {
            return preferredSlot;
        }

        /**
         * The owner key.
         *
         * @return the owner key; derived as in 07 §1.1 when empty (the default)
         */
        public Optional<String> ownerKey() {
            return ownerKey;
        }

        @Override
        public Optional<String> namespace() {
            return namespace;
        }

        /**
         * This machine in another namespace.
         *
         * @param namespace the namespace of the slot leases
         * @return the machine
         */
        public Leased withNamespace(String namespace) {
            Builder builder = new Builder(store).bits(bits).ttl(ttl).tolerance(tolerance).acquireTimeout(acquireTimeout).slotOffset(slotOffset)
                .namespace(namespace);
            preferredSlot.ifPresent(builder::preferredSlot);
            ownerKey.ifPresent(builder::ownerKey);
            return builder.build();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Leased that && store.equals(that.store) && bits == that.bits && ttl.equals(that.ttl)
                && tolerance.equals(that.tolerance) && acquireTimeout.equals(that.acquireTimeout) && slotOffset == that.slotOffset
                && preferredSlot.equals(that.preferredSlot) && ownerKey.equals(that.ownerKey) && namespace.equals(that.namespace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(store, bits, ttl, tolerance, acquireTimeout, slotOffset, preferredSlot, ownerKey, namespace);
        }

        @Override
        public String toString() {
            return "Leased[store=" + store + ", bits=" + bits + ", ttl=" + ttl + ", tolerance=" + tolerance + ", acquireTimeout=" + acquireTimeout
                + ", slotOffset=" + slotOffset + ", preferredSlot=" + preferredSlot + ", ownerKey=" + ownerKey + ", namespace=" + namespace + "]";
        }

        /**
         * Builds a {@link Leased}.
         */
        public static final class Builder {
            private final String store;
            private int bits = 10;
            private Duration ttl = Duration.ofSeconds(30);
            private Duration tolerance = Duration.ofSeconds(1);
            private Duration acquireTimeout;
            private int slotOffset;
            private OptionalInt preferredSlot = OptionalInt.empty();
            private String ownerKey;
            private String namespace;

            private Builder(String store) {
                this.store = Objects.requireNonNull(store, "store");
            }

            /**
             * Sets the slot bits.
             *
             * @param bits the slots are {@code [0, 2^bits)}; default 10
             * @return this builder
             */
            public Builder bits(int bits) {
                this.bits = bits;
                return this;
            }

            /**
             * Sets the lease time to live.
             *
             * @param ttl the ttl; default 30s
             * @return this builder
             */
            public Builder ttl(Duration ttl) {
                this.ttl = Objects.requireNonNull(ttl, "ttl");
                return this;
            }

            /**
             * Sets how far beyond the expiry the ceiling lies (D20).
             *
             * @param tolerance the tolerance; default 1s
             * @return this builder
             */
            public Builder tolerance(Duration tolerance) {
                this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
                return this;
            }

            /**
             * Sets how long {@code CosIdRuntime.start} waits for a slot while every slot is held (D42).
             *
             * @param acquireTimeout the timeout, not negative; {@link Duration#ZERO} tries once; default the ttl
             * @return this builder
             */
            public Builder acquireTimeout(Duration acquireTimeout) {
                this.acquireTimeout = Objects.requireNonNull(acquireTimeout, "acquireTimeout");
                return this;
            }

            /**
             * Sets the offset added to the slot to give the node of the ids (api-freeze.md §2.1.1); it never changes once
             * used, and every client of the namespace uses the same one.
             *
             * @param slotOffset the offset, not negative; default 0
             * @return this builder
             */
            public Builder slotOffset(int slotOffset) {
                this.slotOffset = slotOffset;
                return this;
            }

            /**
             * Sets the slot to try first, such as a StatefulSet ordinal.
             *
             * @param preferredSlot the slot; default none
             * @return this builder
             */
            public Builder preferredSlot(int preferredSlot) {
                this.preferredSlot = OptionalInt.of(preferredSlot);
                return this;
            }

            /**
             * Sets the owner key.
             *
             * @param ownerKey the owner key; derived as in 07 §1.1 by default
             * @return this builder
             */
            public Builder ownerKey(String ownerKey) {
                this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
                return this;
            }

            /**
             * Sets the namespace of the slot leases (D37).
             *
             * @param namespace the namespace; default the {@link RuntimeSpec#namespace()}
             * @return this builder
             */
            public Builder namespace(String namespace) {
                this.namespace = Objects.requireNonNull(namespace, "namespace");
                return this;
            }

            /**
             * Builds the leased slot.
             *
             * @return the leased slot
             */
            public Leased build() {
                return new Leased(this);
            }
        }
    }

    /**
     * A slot the user keeps unique, with a local window file.
     */
    final class Static implements MachineSpec {
        private final int bits;
        private final int slot;
        private final Optional<Path> stateFile;
        private final Duration ahead;
        private final Optional<Instant> notBefore;
        private final Optional<String> namespace;

        private Static(Builder builder) {
            this.bits = builder.bits;
            this.slot = builder.slot;
            this.stateFile = Optional.ofNullable(builder.stateFile);
            this.ahead = builder.ahead;
            this.notBefore = Optional.ofNullable(builder.notBefore);
            this.namespace = Optional.ofNullable(builder.namespace);
        }

        /**
         * A builder of a static slot.
         *
         * @param slot the slot
         * @return the builder
         */
        public static Builder builder(int slot) {
            return new Builder(slot);
        }

        /**
         * The slot bits.
         *
         * @return the slot bits; default 10
         */
        @Override
        public int bits() {
            return bits;
        }

        /**
         * The slot.
         *
         * @return the slot
         */
        public int slot() {
            return slot;
        }

        /**
         * The window file; none means no clock regression protection.
         *
         * @return the window file; default none
         */
        public Optional<Path> stateFile() {
            return stateFile;
        }

        /**
         * How far ahead the ceiling is written.
         *
         * @return the distance; default 10s
         */
        public Duration ahead() {
            return ahead;
        }

        /**
         * The floor when the file does not exist yet (given by the migration tool).
         *
         * @return the floor; default none
         */
        public Optional<Instant> notBefore() {
            return notBefore;
        }

        @Override
        public Optional<String> namespace() {
            return namespace;
        }

        /**
         * This machine in another namespace.
         *
         * @param namespace the namespace
         * @return the machine
         */
        public Static withNamespace(String namespace) {
            Builder builder = new Builder(slot).bits(bits).ahead(ahead).namespace(namespace);
            stateFile.ifPresent(builder::stateFile);
            notBefore.ifPresent(builder::notBefore);
            return builder.build();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Static that && bits == that.bits && slot == that.slot && stateFile.equals(that.stateFile)
                && ahead.equals(that.ahead) && notBefore.equals(that.notBefore) && namespace.equals(that.namespace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bits, slot, stateFile, ahead, notBefore, namespace);
        }

        @Override
        public String toString() {
            return "Static[bits=" + bits + ", slot=" + slot + ", stateFile=" + stateFile + ", ahead=" + ahead + ", notBefore=" + notBefore
                + ", namespace=" + namespace + "]";
        }

        /**
         * Builds a {@link Static}.
         */
        public static final class Builder {
            private final int slot;
            private int bits = 10;
            private Path stateFile;
            private Duration ahead = Duration.ofSeconds(10);
            private Instant notBefore;
            private String namespace;

            private Builder(int slot) {
                this.slot = slot;
            }

            /**
             * Sets the slot bits.
             *
             * @param bits the slot bits; default 10
             * @return this builder
             */
            public Builder bits(int bits) {
                this.bits = bits;
                return this;
            }

            /**
             * Sets the window file.
             *
             * @param stateFile the window file; default none, which means no clock regression protection
             * @return this builder
             */
            public Builder stateFile(Path stateFile) {
                this.stateFile = Objects.requireNonNull(stateFile, "stateFile");
                return this;
            }

            /**
             * Sets how far ahead the ceiling is written.
             *
             * @param ahead the distance; default 10s
             * @return this builder
             */
            public Builder ahead(Duration ahead) {
                this.ahead = Objects.requireNonNull(ahead, "ahead");
                return this;
            }

            /**
             * Sets the floor when the file does not exist yet (given by the migration tool).
             *
             * @param notBefore the floor; default none
             * @return this builder
             */
            public Builder notBefore(Instant notBefore) {
                this.notBefore = Objects.requireNonNull(notBefore, "notBefore");
                return this;
            }

            /**
             * Sets the namespace the machine belongs to (D37); a static machine leases nothing, but its namespace tells
             * the machines of a runtime apart in events, metrics and health.
             *
             * @param namespace the namespace; default the {@link RuntimeSpec#namespace()}
             * @return this builder
             */
            public Builder namespace(String namespace) {
                this.namespace = Objects.requireNonNull(namespace, "namespace");
                return this;
            }

            /**
             * Builds the static slot.
             *
             * @return the static slot
             */
            public Static build() {
                return new Static(this);
            }
        }
    }
}
