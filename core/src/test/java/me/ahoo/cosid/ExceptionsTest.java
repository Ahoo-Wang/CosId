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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExceptionsTest {
    @Test
    void exceptionsCarryTheirContext() {
        ClockBackwardsException clock = new ClockBackwardsException("order", 7);
        assertThat(clock.generator(), is("order"));
        assertThat(clock.backwardsMillis(), is(7L));
        assertThat(clock.getMessage(), containsString("7 ms"));

        SlotLostException lost = new SlotLostException("order", 3);
        assertThat(lost.generator(), is("order"));
        assertThat(lost.slot(), is(3));

        SlotWindowExceededException window = new SlotWindowExceededException("order", 11, 10);
        assertThat(window.generator(), is("order"));
        assertThat(window.timestampMillis(), is(11L));
        assertThat(window.ceiling(), is(10L));

        assertThat(new RangeSealedException("app/order").key(), is("app/order"));
        assertThat(new RangeExhaustedException("app/order").key(), is("app/order"));
        IllegalStateException cause = new IllegalStateException("down");
        RangeUnavailableException unavailable = new RangeUnavailableException("app/order", cause);
        assertThat(unavailable.key(), is("app/order"));
        assertThat(unavailable.getCause(), sameInstance(cause));
        assertThat(new IdOverflowException(1L << 40).id(), is(1L << 40));
        assertThat(new GeneratorDisabledException("bill").generator(), is("bill"));
        NamespaceMismatchException mismatch = new NamespaceMismatchException("app", "limit 1024 != 512");
        assertThat(mismatch.namespace(), is("app"));
        assertThat(mismatch.getMessage(), containsString("limit 1024 != 512"));
        assertThat(new CosIdException("boom", cause).getCause(), sameInstance(cause));
    }

    @Test
    void stringIdGeneratorEncodesEachId() {
        StringIdGenerator generator = new StringIdGenerator(() -> 42, new me.ahoo.cosid.codec.IdCodec() {
            @Override
            public String encode(long id) {
                return "#" + id;
            }

            @Override
            public long decode(String text) {
                return Long.parseLong(text.substring(1));
            }
        });
        assertThat(generator.generate(), is("#42"));
        assertThrows(NullPointerException.class, () -> new StringIdGenerator(null, generator.codec()));
        assertThat(new GroupedId("20261026", 1).period(), is("20261026"));
        assertThrows(NullPointerException.class, () -> new GroupedId(null, 1));
    }
}
