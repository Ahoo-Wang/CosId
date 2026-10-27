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

package me.ahoo.cosid.time;

import me.ahoo.cosid.lease.SlotBinding;
import me.ahoo.cosid.lease.SlotHandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * A test-only slot that rebinds, raises the ceiling and invalidates on demand.
 *
 * <p>TODO(R1-C): {@link #raiseCeiling(long)} sets the ceiling field of the binding through a {@link VarHandle} until
 * {@code SlotBinding.raiseCeiling} is implemented; then it calls that.
 */
final class FakeSlot implements SlotHandle {
    private static final VarHandle CEILING;

    static {
        try {
            CEILING = MethodHandles.privateLookupIn(SlotBinding.class, MethodHandles.lookup())
                .findVarHandle(SlotBinding.class, "ceiling", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile SlotBinding current;

    FakeSlot(int slot, long floor, long ceiling) {
        this.current = new SlotBinding(slot, floor, ceiling, 0);
    }

    static FakeSlot forever(int slot) {
        return new FakeSlot(slot, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    @Override
    public SlotBinding current() {
        return current;
    }

    SlotBinding rebind(int slot, long floor, long ceiling) {
        SlotBinding next = new SlotBinding(slot, floor, ceiling, current.generation() + 1);
        current = next;
        return next;
    }

    void raiseCeiling(long ceiling) {
        SlotBinding binding = current;
        for (long now = (long) CEILING.getVolatile(binding); ceiling > now; now = (long) CEILING.getVolatile(binding)) {
            if (CEILING.compareAndSet(binding, now, ceiling)) {
                return;
            }
        }
    }

    void invalidate() {
        current.invalidate();
    }
}
