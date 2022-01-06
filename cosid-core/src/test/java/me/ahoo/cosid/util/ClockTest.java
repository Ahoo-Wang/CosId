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

package me.ahoo.cosid.util;

import com.google.common.base.Strings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * @author ahoo wang
 */
public class ClockTest {

//    @Test
    public void cacheClock() {
        final long MAX_TRY_TIMES = 1000;
        final long parkNanos = Duration.ofMillis(100).toNanos();
        int currentTimes = 0;
        int diffTimes = 0;

        while (currentTimes < MAX_TRY_TIMES) {
            currentTimes++;
            long cacheSecond = Clock.CACHE.secondTime();
            long systemSecond = Clock.getSystemSecondTime();
            long diff = systemSecond - cacheSecond;
            if (diff != 0) {
                diffTimes++;
            }
            System.out.println(Strings.lenientFormat("cacheTime:[%s] - systemTime:[%s] - diff:[%s] - diffTimes:[%s]", cacheSecond, systemSecond, diff, diffTimes));
            Assertions.assertTrue(diff <= 1);
            LockSupport.parkNanos(this, parkNanos);
        }

    }
}
