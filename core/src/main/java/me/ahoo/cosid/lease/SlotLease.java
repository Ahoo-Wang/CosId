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
 * milliseconds since the Unix epoch. Immutable; built with {@link #builder()}, the only way to create one, so fields
 * can be added with a default (api-freeze.md §4); {@link #toBuilder()} derives one.
 */
public final class SlotLease {
    private final String namespace;
    private final int slot;
    private final Owner owner;
    private final long fence;
    private final long expiresAt;
    private final long floor;
    private final long ceiling;
    private final long storeNow;

    private SlotLease(Builder builder) {
        this.namespace = Objects.requireNonNull(builder.namespace, "namespace");
        this.slot = builder.slot;
        this.owner = Objects.requireNonNull(builder.owner, "owner");
        this.fence = builder.fence;
        this.expiresAt = builder.expiresAt;
        this.floor = builder.floor;
        this.ceiling = builder.ceiling;
        this.storeNow = builder.storeNow;
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative: " + slot);
        }
        if (ceiling < floor) {
            throw new IllegalArgumentException("ceiling " + ceiling + " must not be below floor " + floor);
        }
    }

    /**
     * A builder of a lease; the namespace and the owner are required, the numbers default to 0.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder holding this lease, to derive another.
     *
     * @return the builder
     */
    public Builder toBuilder() {
        return new Builder().namespace(namespace).slot(slot).owner(owner).fence(fence).expiresAt(expiresAt).floor(floor).ceiling(ceiling)
            .storeNow(storeNow);
    }

    /**
     * The namespace.
     *
     * @return the namespace
     */
    public String namespace() {
        return namespace;
    }

    /**
     * The slot, in {@code [0, limit)}.
     *
     * @return the slot
     */
    public int slot() {
        return slot;
    }

    /**
     * The holder.
     *
     * @return the owner
     */
    public Owner owner() {
        return owner;
    }

    /**
     * Raised on every ownership change (acquire, takeover, release); unchanged by renewals.
     *
     * @return the fence
     */
    public long fence() {
        return fence;
    }

    /**
     * When others may take the slot over.
     *
     * @return the expiry
     */
    public long expiresAt() {
        return expiresAt;
    }

    /**
     * The holder issues timestamps strictly above it.
     *
     * @return the floor
     */
    public long floor() {
        return floor;
    }

    /**
     * The holder issues timestamps up to it, inclusive.
     *
     * @return the ceiling
     */
    public long ceiling() {
        return ceiling;
    }

    /**
     * The store clock of the write that produced this lease.
     *
     * @return the store time
     */
    public long storeNow() {
        return storeNow;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SlotLease that && namespace.equals(that.namespace) && slot == that.slot && owner.equals(that.owner)
            && fence == that.fence && expiresAt == that.expiresAt && floor == that.floor && ceiling == that.ceiling && storeNow == that.storeNow;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, slot, owner, fence, expiresAt, floor, ceiling, storeNow);
    }

    @Override
    public String toString() {
        return "SlotLease[namespace=" + namespace + ", slot=" + slot + ", owner=" + owner + ", fence=" + fence + ", expiresAt=" + expiresAt
            + ", floor=" + floor + ", ceiling=" + ceiling + ", storeNow=" + storeNow + "]";
    }

    /**
     * Builds a {@link SlotLease}; {@link #build()} checks the fields.
     */
    public static final class Builder {
        private String namespace;
        private int slot;
        private Owner owner;
        private long fence;
        private long expiresAt;
        private long floor;
        private long ceiling;
        private long storeNow;

        private Builder() {
        }

        /**
         * Sets the namespace; required.
         *
         * @param namespace the namespace
         * @return this builder
         */
        public Builder namespace(String namespace) {
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            return this;
        }

        /**
         * Sets the slot.
         *
         * @param slot the slot, not negative
         * @return this builder
         */
        public Builder slot(int slot) {
            this.slot = slot;
            return this;
        }

        /**
         * Sets the holder; required.
         *
         * @param owner the owner
         * @return this builder
         */
        public Builder owner(Owner owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
            return this;
        }

        /**
         * Sets the fence.
         *
         * @param fence the fence
         * @return this builder
         */
        public Builder fence(long fence) {
            this.fence = fence;
            return this;
        }

        /**
         * Sets when others may take the slot over.
         *
         * @param expiresAt the expiry
         * @return this builder
         */
        public Builder expiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /**
         * Sets the floor: the holder issues timestamps strictly above it.
         *
         * @param floor the floor
         * @return this builder
         */
        public Builder floor(long floor) {
            this.floor = floor;
            return this;
        }

        /**
         * Sets the ceiling: the holder issues timestamps up to it, inclusive.
         *
         * @param ceiling the ceiling, not below the floor
         * @return this builder
         */
        public Builder ceiling(long ceiling) {
            this.ceiling = ceiling;
            return this;
        }

        /**
         * Sets the store clock of the write that produced the lease.
         *
         * @param storeNow the store time
         * @return this builder
         */
        public Builder storeNow(long storeNow) {
            this.storeNow = storeNow;
            return this;
        }

        /**
         * Builds the lease.
         *
         * @return the lease
         * @throws NullPointerException     if the namespace or the owner is not set
         * @throws IllegalArgumentException if the slot is negative or the ceiling is below the floor
         */
        public SlotLease build() {
            return new SlotLease(this);
        }
    }
}
