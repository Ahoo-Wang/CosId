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

import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Small combinators that replace the 4.x generator classes of the same purpose (03-core-model.md §5).
 */
public final class IdGenerators {
    private IdGenerators() {
    }

    /**
     * Narrows the ids of {@code ids} to {@code int}; replaces 4.x {@code IntegerIdGenerator}.
     *
     * @param ids the generator
     * @return a generator that throws {@link IdOverflowException} for an id beyond {@code int}
     */
    public static IntIdGenerator toInt(IdGenerator ids) {
        throw new UnsupportedOperationException("R1-A");
    }

    /**
     * Appends {@code bits} random low bits to each id; replaces 4.x {@code UncertaintyIdGenerator}.
     *
     * @param ids    the generator
     * @param bits   the number of random low bits
     * @param random the source of the random bits
     * @return the generator
     */
    public static IdGenerator uncertain(IdGenerator ids, int bits, RandomGenerator random) {
        throw new UnsupportedOperationException("R1-A");
    }

    /**
     * An in-process counter; replaces 4.x {@code AtomicLongGenerator}.
     *
     * @return the generator
     */
    public static IdGenerator local() {
        throw new UnsupportedOperationException("R1-A");
    }

    /**
     * Random UUID text; replaces 4.x {@code UuidGenerator}.
     *
     * @return the supplier
     */
    public static Supplier<String> uuid() {
        throw new UnsupportedOperationException("R1-A");
    }

    /**
     * Looks the generator up on first use; replaces 4.x {@code LazyIdGenerator}.
     *
     * @param lookup supplies the generator
     * @return the generator
     */
    public static IdGenerator lazy(Supplier<? extends IdGenerator> lookup) {
        throw new UnsupportedOperationException("R1-A");
    }
}
