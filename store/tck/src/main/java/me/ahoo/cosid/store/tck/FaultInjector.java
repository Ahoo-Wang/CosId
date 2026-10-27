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

package me.ahoo.cosid.store.tck;

/**
 * The faults a {@link FaultyRangeStore} or {@link FaultySlotLeaseStore} applies, per operation: one-shot faults in the
 * order they were queued, then the standing fault if any. Thread-safe.
 */
public final class FaultInjector {
    /**
     * A store operation.
     */
    public enum Operation {
        RESERVE,
        SEAL,
        ACQUIRE,
        RENEW,
        RELEASE
    }

    /**
     * Applies {@code fault} to the next call of {@code operation} only.
     *
     * @param operation the operation
     * @param fault     the fault
     * @return this injector
     */
    public FaultInjector once(Operation operation, Fault fault) {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * Applies {@code fault} to every call of {@code operation} until {@link #clear()}.
     *
     * @param operation the operation
     * @param fault     the fault
     * @return this injector
     */
    public FaultInjector always(Operation operation, Fault fault) {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * Removes every fault.
     */
    public void clear() {
        throw new UnsupportedOperationException("R1-C");
    }

    /**
     * How many calls of {@code operation} reached the wrapped store.
     *
     * @param operation the operation
     * @return the count
     */
    public long calls(Operation operation) {
        throw new UnsupportedOperationException("R1-C");
    }
}
