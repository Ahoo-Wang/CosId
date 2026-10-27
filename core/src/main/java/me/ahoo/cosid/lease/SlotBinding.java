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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * One holding of a slot and its time window (05-machine-lease.md §4.1). A renewal raises {@link #ceiling()} in place;
 * a new acquisition publishes a new binding with the next {@link #generation()}; a lost lease invalidates the binding.
 *
 * <p>Generators keep their sequencer state per binding, so it resets with every new binding.
 */
public final class SlotBinding {
    private static final VarHandle CEILING;

    static {
        try {
            CEILING = MethodHandles.lookup().findVarHandle(SlotBinding.class, "ceiling", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final int slot;
    private final long floor;
    private final long generation;
    private volatile long ceiling;
    private volatile boolean valid = true;

    /**
     * A binding.
     *
     * @param slot       the slot
     * @param floor      timestamps must be strictly above it, in milliseconds
     * @param ceiling    timestamps must be at most it, in milliseconds
     * @param generation counts the bindings of a {@link SlotHandle}
     */
    public SlotBinding(int slot, long floor, long ceiling, long generation) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative: " + slot);
        }
        if (ceiling < floor) {
            throw new IllegalArgumentException("ceiling " + ceiling + " must not be below floor " + floor);
        }
        this.slot = slot;
        this.floor = floor;
        this.ceiling = ceiling;
        this.generation = generation;
    }

    public int slot() {
        return slot;
    }

    public long floor() {
        return floor;
    }

    public long ceiling() {
        return ceiling;
    }

    public long generation() {
        return generation;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Raises the ceiling after a renewal; a lower value is ignored, the ceiling never goes down.
     *
     * @param newCeiling the renewed ceiling
     */
    public void raiseCeiling(long newCeiling) {
        long current;
        do {
            current = ceiling;
            if (newCeiling <= current) {
                return;
            }
        } while (!CEILING.compareAndSet(this, current, newCeiling));
    }

    /**
     * Marks the binding lost: generators reading it throw {@code SlotLostException}.
     */
    public void invalidate() {
        valid = false;
    }
}
