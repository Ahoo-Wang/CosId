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
 * UUIDv7 ids (D46, RFC 9562 §5.7): a 48-bit Unix millisecond timestamp, version 7, a 16-bit counter (the 12 bits of
 * {@code rand_a} and the top 4 bits of {@code rand_b}), variant {@code 0b10} and 58 random bits. It needs no machine,
 * namespace or lease: ids are unique without coordination through their random counter start and random bits.
 *
 * <p>Ordered by method 1 of RFC 9562 §6.2, with a dedicated 16-bit counter (the RFC allows 12 to 42 bits): it starts at
 * a random value below 2<sup>15</sup> in each millisecond, which leaves at least 2<sup>15</sup> (32 768) ids per
 * millisecond, and the millisecond and the counter move together in one {@link AtomicLong}, 48 + 16 bits. The ids of
 * one instance therefore strictly increase, as {@link UUID}s and as text. The timestamp runs ahead of the clock in two
 * cases only: more than 2<sup>15</sup> ids in one millisecond, kept up (a full counter carries into the next
 * millisecond), and a clock that goes back (the last timestamp is kept and the counter goes on until the clock catches
 * up). Ids of different instances or processes are ordered by their millisecond only.
 *
 * <p>The random bits come from {@link ThreadLocalRandom}: the ids are unique, not unpredictable. An id is no secret:
 * never use one as a token.
 */
public final class UuidV7Generator implements Supplier<String> {
    /** The bits of the counter: {@code rand_a} and the top {@link #COUNTER_LOW_BITS} bits of {@code rand_b}. */
    static final int COUNTER_BITS = 16;
    /** The bound of the counter's random start in a new millisecond: its top bit clear, for headroom. */
    static final long START_BOUND = 1L << (COUNTER_BITS - 1);
    /** The bits of the Unix millisecond timestamp. */
    static final int TIMESTAMP_BITS = 48;
    private static final int RAND_A_BITS = 12;
    private static final int COUNTER_LOW_BITS = COUNTER_BITS - RAND_A_BITS;
    private static final int RANDOM_BITS = 62 - COUNTER_LOW_BITS;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;
    private static final long COUNTER_LOW_MASK = (1L << COUNTER_LOW_BITS) - 1;
    private static final long RANDOM_MASK = (1L << RANDOM_BITS) - 1;
    private static final long VERSION = 7L << RAND_A_BITS;
    private static final long VARIANT = 2L << 62;

    private final String name;
    private final TimeSource time;
    /** The last id's {@code millis << 16 | counter}, unsigned. */
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
        long counter = state & COUNTER_MASK;
        long mostSignificant = millis << 16 | VERSION | counter >>> COUNTER_LOW_BITS;
        long leastSignificant = VARIANT | (counter & COUNTER_LOW_MASK) << RANDOM_BITS | (ThreadLocalRandom.current().nextLong() & RANDOM_MASK);
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

    /** The next state: a new millisecond starts the counter at random, the rest count on. */
    private long next() {
        long now = time.currentMillis();
        if (now >>> TIMESTAMP_BITS != 0 && now > 0) {
            throw new TimeOverflowException(name, now, TIMESTAMP_BITS);
        }
        while (true) {
            long previous = last.get();
            long next;
            if (now > previous >>> COUNTER_BITS) {
                next = now << COUNTER_BITS | ThreadLocalRandom.current().nextLong(START_BOUND);
            } else {
                // The same millisecond, or a clock gone back: count on; a full counter carries into the millisecond.
                next = previous + 1;
                if (next == 0) {
                    throw new TimeOverflowException(name, 1L << TIMESTAMP_BITS, TIMESTAMP_BITS);
                }
            }
            if (last.compareAndSet(previous, next)) {
                return next;
            }
        }
    }

    /** Sets the state, for tests of the counter at its bound. */
    void state(long millis, long counter) {
        last.set(millis << COUNTER_BITS | counter);
    }

    @Override
    public String toString() {
        return "UuidV7Generator[" + name + "]";
    }
}
