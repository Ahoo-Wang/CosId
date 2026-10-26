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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.lease.SlotBinding;
import me.ahoo.cosid.observe.GeneratorListener;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class TimeModelTest {
    @Test
    void clockRegressionPolicyPresets() {
        assertThat(ClockRegressionPolicy.DEFAULT.borrowUpTo(), is(Duration.ofMillis(5)));
        assertThat(ClockRegressionPolicy.DEFAULT.waitUpTo(), is(Duration.ofSeconds(1)));
        assertThat(ClockRegressionPolicy.FAIL.waitUpTo(), is(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ClockRegressionPolicy(Duration.ofMillis(-1), Duration.ZERO));
    }

    @Test
    void timeSourceSystemReadsTheClock() {
        long before = System.currentTimeMillis();
        assertThat(TimeSource.SYSTEM.currentMillis() >= before, is(true));
    }

    @Test
    void generatorKeepsItsConfiguration() {
        SlotBinding binding = new SlotBinding(1, 0, Long.MAX_VALUE, 0);
        TimeBasedGenerator generator = new TimeBasedGenerator("order", Layout.SNOWFLAKE_MS, () -> binding, TimeSource.SYSTEM,
            ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP);
        assertThat(generator.name(), is("order"));
        assertThat(generator.layout(), is(Layout.SNOWFLAKE_MS));
        assertThrows(NullPointerException.class, () -> new TimeBasedGenerator("order", null, () -> binding, TimeSource.SYSTEM,
            ClockRegressionPolicy.DEFAULT, GeneratorListener.NOOP));
    }
}
