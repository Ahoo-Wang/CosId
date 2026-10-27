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

package me.ahoo.cosid;

/**
 * The machine slot binding a generator read was invalidated: its lease was lost (05-machine-lease.md §4.2).
 */
public class SlotLostException extends CosIdException {
    private final String generator;
    private final int slot;

    public SlotLostException(String generator, int slot) {
        super("Lost the lease of slot " + slot + " in generator [" + generator + "]");
        this.generator = generator;
        this.slot = slot;
    }

    public String generator() {
        return generator;
    }

    public int slot() {
        return slot;
    }
}
