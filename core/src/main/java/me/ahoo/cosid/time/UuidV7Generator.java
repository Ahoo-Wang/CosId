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

import me.ahoo.cosid.TimeOverflowException;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * UUIDv7 ids (D46, RFC 9562 §5.7): a 48-bit Unix millisecond timestamp, version 7, 12 bits {@code rand_a}, variant
 * {@code 0b10} and 62 bits {@code rand_b}. It needs no machine, namespace or lease: ids are unique without
 * coordination through their 62 random bits.
 *
 * <p>Ordered by method 1 of RFC 9562 §6.2: {@code rand_a} is a counter that starts at a random value below 2048 in
 * each millisecond, which leaves at least 2048 ids per millisecond, and the timestamp and the counter move together in
 * one {@link AtomicLong}. The ids of one instance therefore strictly increase, as {@link UUID}s and as text. A full
 * counter carries into the timestamp (the next millisecond, borrowed); a clock that goes back keeps the last timestamp
 * and goes on counting. Ids of different instances or processes are ordered by their millisecond only.
 *
 * <p>{@code rand_b} comes from {@link ThreadLocalRandom}: the ids are unique, not unpredictable. An id is no secret:
 * never use one as a token.
 */
public final class UuidV7Generator implements Supplier<String> {
    /** The bits of {@code rand_a}, the counter. */
    static final int COUNTER_BITS = 12;
    /** The bound of the counter's random start in a new millisecond: its top bit clear, for headroom. */
    static final int START_BOUND = 1 << (COUNTER_BITS - 1);
    /** The bits of the Unix millisecond timestamp. */
    static final int TIMESTAMP_BITS = 48;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;
    private static final long VERSION = 7L << COUNTER_BITS;
    private static final long VARIANT = 2L << 62;
    private static final long RAND_B_MASK = (1L << 62) - 1;

    private final String name;
    private final TimeSource time;
    /** The timestamp and the counter of the last id: {@code millis << 12 | counter}. */
    private final AtomicLong last = new AtomicLong();

    /**
     * A generator on a time source.
     *
     * @param name the generator name, for messages
     * @param time the time source; {@link TimeSource#SYSTEM} outside tests
     */
    public UuidV7Generator(String name, TimeSource time) {
        this.name = Objects.requireNonNull(name, "name");
        this.time = Objects.requireNonNull(time, "time");
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The next id.
     *
     * @return a version 7, variant 2 {@link UUID}, greater than every id this instance issued before
     * @throws TimeOverflowException if the timestamp passes 48 bits (the year 10889)
     */
    public UUID generateUuid() {
        long state = next();
        long millis = state >>> COUNTER_BITS;
        if (millis >>> TIMESTAMP_BITS != 0) {
            throw new TimeOverflowException(name, millis, TIMESTAMP_BITS);
        }
        long mostSignificant = millis << 16 | VERSION | (state & COUNTER_MASK);
        long leastSignificant = VARIANT | (ThreadLocalRandom.current().nextLong() & RAND_B_MASK);
        return new UUID(mostSignificant, leastSignificant);
    }

    /**
     * The next id as text.
     *
     * @return the canonical 36-character form, lower case
     */
    public String generate() {
        return generateUuid().toString();
    }

    /**
     * The next id as text: {@link #generate()}.
     *
     * @return the canonical form
     */
    @Override
    public String get() {
        return generate();
    }

    /** The next {@code millis << 12 | counter}: a new millisecond starts the counter at random, the rest count on. */
    private long next() {
        long now = time.currentMillis();
        while (true) {
            long previous = last.get();
            long next = now > previous >>> COUNTER_BITS
                ? now << COUNTER_BITS | ThreadLocalRandom.current().nextInt(START_BOUND)
                // The same millisecond, or a clock gone back: count on; a full counter carries into the timestamp.
                : previous + 1;
            if (last.compareAndSet(previous, next)) {
                return next;
            }
        }
    }

    @Override
    public String toString() {
        return "UuidV7Generator[" + name + "]";
    }
}
