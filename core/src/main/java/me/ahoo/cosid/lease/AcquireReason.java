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

import java.util.Locale;

/**
 * Which step of the slot choice (05-machine-lease.md §2.1) gave the slot; the store reports it, for observation only.
 */
public enum AcquireReason {
    /** The owner, same key and incarnation, already held the slot: a retried acquire. */
    IDEMPOTENT,
    /** The free preferred slot. */
    PREFERRED,
    /** The free slot the owner key held last. */
    STICKY,
    /** The lowest free slot. */
    LOWEST;

    /**
     * The name the listener events use ({@code idempotent}, {@code preferred}, {@code sticky}, {@code lowest}).
     *
     * @return the lower case name
     */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
