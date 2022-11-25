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

package me.ahoo.cosid.machine;

import static me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultClockBackwardsSynchronizerTest {
    
    @SneakyThrows
    @Test
    void sync() {
        DefaultClockBackwardsSynchronizer clockBackwardsSynchronizer = new DefaultClockBackwardsSynchronizer();
        long currentTimeMillis = System.currentTimeMillis();
        clockBackwardsSynchronizer.sync(currentTimeMillis);
        clockBackwardsSynchronizer.sync(currentTimeMillis - 10);
        clockBackwardsSynchronizer.sync(currentTimeMillis + 10);
        Assertions.assertThrows(ClockTooManyBackwardsException.class, () -> {
            clockBackwardsSynchronizer.sync(currentTimeMillis + DEFAULT_BROKEN_THRESHOLD + 100);
        });
    }
}
