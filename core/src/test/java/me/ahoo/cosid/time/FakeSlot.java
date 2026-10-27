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

/**
 * A test-only slot that rebinds, raises the ceiling and invalidates on demand.
 */
final class FakeSlot implements SlotHandle {
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
        current.raiseCeiling(ceiling);
    }

    void invalidate() {
        current.invalidate();
    }
}
