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

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Small combinators that replace the 4.x generator classes of the same purpose (03-core-model.md §5).
 */
public final class IdGenerators {
    /** The most random bits {@link #uncertain} appends: one bit at least stays for the id. */
    private static final int MAX_UNCERTAIN_BITS = 62;

    private IdGenerators() {
    }

    /**
     * Narrows the ids of {@code ids} to {@code int}; replaces 4.x {@code IntegerIdGenerator}.
     *
     * @param ids the generator
     * @return a generator that throws {@link IdOverflowException} for an id beyond {@code int}
     */
    public static IntIdGenerator toInt(IdGenerator ids) {
        Objects.requireNonNull(ids, "ids");
        return () -> {
            long id = ids.generate();
            if (id != (int) id) {
                throw new IdOverflowException(id);
            }
            return (int) id;
        };
    }

    /**
     * Appends {@code bits} random low bits to each id; replaces 4.x {@code UncertaintyIdGenerator}.
     *
     * @param ids    the generator
     * @param bits   the number of random low bits
     * @param random the source of the random bits
     * @return the generator; it throws {@link IdOverflowException} for a negative id or one that leaves no room for the bits
     */
    public static IdGenerator uncertain(IdGenerator ids, int bits, RandomGenerator random) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(random, "random");
        if (bits < 1 || bits > MAX_UNCERTAIN_BITS) {
            throw new IllegalArgumentException("bits must be in [1, " + MAX_UNCERTAIN_BITS + "]: " + bits);
        }
        long maxId = Long.MAX_VALUE >>> bits;
        long bound = 1L << bits;
        return () -> {
            long id = ids.generate();
            if (id < 0 || id > maxId) {
                throw new IdOverflowException(id, "id " + id + " leaves no room for " + bits + " random bits");
            }
            return id << bits | random.nextLong(bound);
        };
    }

    /**
     * An in-process counter; replaces 4.x {@code AtomicLongGenerator}.
     *
     * @return the generator
     */
    public static IdGenerator local() {
        AtomicLong counter = new AtomicLong();
        return counter::incrementAndGet;
    }

    /**
     * Random UUID text; replaces 4.x {@code UuidGenerator}.
     *
     * @return the supplier
     */
    public static Supplier<String> uuid() {
        return () -> UUID.randomUUID().toString();
    }

    /**
     * Looks the generator up on first use; replaces 4.x {@code LazyIdGenerator}.
     *
     * @param lookup supplies the generator
     * @return the generator
     */
    public static IdGenerator lazy(Supplier<? extends IdGenerator> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return new IdGenerator() {
            private volatile IdGenerator ids;

            @Override
            public long generate() {
                IdGenerator current = ids;
                if (current == null) {
                    current = Objects.requireNonNull(lookup.get(), "lookup returned null");
                    ids = current;
                }
                return current.generate();
            }
        };
    }
}
