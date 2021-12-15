/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ahoo wang
 */
class CacheClock {

    @Test
    void secondTime() {
        long actual = Clock.CACHE.secondTime();
        long expected = Clock.getSystemSecondTime();
        assertEquals(expected, actual);
    }

    @SneakyThrows
    @Test
    void secondTimeWhenSleep() {
        TimeUnit.SECONDS.sleep(1);
        long actual = Clock.CACHE.secondTime();
        long expected = Clock.getSystemSecondTime();
        long diff = Math.abs(actual - expected);
        long tolerance = 1L;
        assertTrue(diff <= tolerance);
    }
}
