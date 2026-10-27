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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class IdGeneratorsTest {
    @Test
    void toIntNarrowsOrThrows() {
        long[] next = {Integer.MAX_VALUE};
        IntIdGenerator ints = IdGenerators.toInt(() -> next[0]);
        assertThat(ints.generate(), is(Integer.MAX_VALUE));
        next[0] = Integer.MIN_VALUE;
        assertThat(ints.generate(), is(Integer.MIN_VALUE));
        next[0] = Integer.MAX_VALUE + 1L;
        IdOverflowException error = assertThrows(IdOverflowException.class, ints::generate);
        assertThat(error.id(), is(Integer.MAX_VALUE + 1L));
    }

    @Test
    void uncertainAppendsRandomLowBits() {
        long[] next = {5};
        IdGenerator ids = IdGenerators.uncertain(() -> next[0], 4, new Random(1));
        for (int i = 0; i < 100; i++) {
            long id = ids.generate();
            assertThat(id >>> 4, is(5L));
        }
        next[0] = Long.MAX_VALUE >>> 3;
        assertThrows(IllegalStateException.class, ids::generate);
        next[0] = -1;
        assertThrows(IllegalStateException.class, ids::generate);
        assertThrows(IllegalArgumentException.class, () -> IdGenerators.uncertain(() -> 1, 0, new Random()));
        assertThrows(IllegalArgumentException.class, () -> IdGenerators.uncertain(() -> 1, 63, new Random()));
    }

    @Test
    void localCounts() {
        IdGenerator local = IdGenerators.local();
        assertThat(local.generate(), is(1L));
        assertThat(local.generate(), is(2L));
    }

    @Test
    void uuidText() {
        Supplier<String> uuid = IdGenerators.uuid();
        String text = uuid.get();
        assertThat(UUID.fromString(text).toString(), is(text));
        assertThat(uuid.get().equals(text), is(false));
    }

    @Test
    void lazyLooksUpOnFirstUseOnly() {
        AtomicInteger lookups = new AtomicInteger();
        IdGenerator lazy = IdGenerators.lazy(() -> {
            lookups.incrementAndGet();
            return () -> 9;
        });
        assertThat(lookups.get(), is(0));
        assertThat(lazy.generate(), is(9L));
        assertThat(lazy.generate(), is(9L));
        assertThat(lookups.get(), is(1));
        assertThrows(NullPointerException.class, () -> IdGenerators.lazy(() -> null).generate());
    }
}
