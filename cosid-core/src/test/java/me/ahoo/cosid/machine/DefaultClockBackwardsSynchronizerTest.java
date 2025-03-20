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

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultClockBackwardsSynchronizerTest {
    DefaultClockBackwardsSynchronizer synchronizer = new DefaultClockBackwardsSynchronizer();

    @SneakyThrows
    @Test
    void sync() {
        synchronizer.sync(System.currentTimeMillis());
        synchronizer.sync(System.currentTimeMillis() + 1);
        synchronizer.sync(System.currentTimeMillis() + 1);
        synchronizer.sync(System.currentTimeMillis() + 1);
        synchronizer.sync(System.currentTimeMillis() - 10);
        synchronizer.sync(System.currentTimeMillis() + 10);
        Assertions.assertThrows(ClockTooManyBackwardsException.class, () -> {
            synchronizer.sync(System.currentTimeMillis() + DEFAULT_BROKEN_THRESHOLD + 100);
        });
    }

    @SneakyThrows
    @Test
    void syncUninterruptibly() {
        long normalTimestamp = System.currentTimeMillis();
        assertDoesNotThrow(() -> synchronizer.syncUninterruptibly(normalTimestamp));
    }

    @SneakyThrows
    @Test
    void syncUninterruptiblyWhenInterrupted() {
        final long triggerTimestamp = System.currentTimeMillis() + DEFAULT_BROKEN_THRESHOLD;

        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            synchronizer.syncUninterruptibly(triggerTimestamp);
        });

        testThread.start();
        testThread.join();
        assertTrue(testThread.isInterrupted());

        // Cleanup
        Thread.interrupted();
    }

    /**
     * TC3: 验证时钟回拨过大异常透传
     */
    @Test
    void syncUninterruptiblyWhenExceedBrokenThreshold() {
        long exceedTimestamp = System.currentTimeMillis() + DEFAULT_BROKEN_THRESHOLD + 100;
        assertThrows(ClockTooManyBackwardsException.class,
            () -> synchronizer.syncUninterruptibly(exceedTimestamp));
    }
}
