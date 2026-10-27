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

import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.tck.FaultInjector.Operation;

import java.util.Objects;

/**
 * A {@link RangeStore} that applies the faults of a {@link FaultInjector} before delegating, for the generator tests
 * of R1 (delays, timeouts, lost responses).
 */
public final class FaultyRangeStore implements RangeStore {
    private final RangeStore delegate;
    private final FaultInjector faults;

    public FaultyRangeStore(RangeStore delegate, FaultInjector faults) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        return faults.apply(Operation.RESERVE, () -> delegate.reserve(key, size, initialOffset));
    }

    @Override
    public boolean seal(String key) {
        return faults.apply(Operation.SEAL, () -> delegate.seal(key));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
