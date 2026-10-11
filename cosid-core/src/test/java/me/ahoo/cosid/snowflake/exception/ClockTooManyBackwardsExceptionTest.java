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

package me.ahoo.cosid.snowflake.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author : Rocher Kong
 */
class ClockTooManyBackwardsExceptionTest {
    private final ClockTooManyBackwardsException clockTooManyBackwardsException = new ClockTooManyBackwardsException(200, 100, 50);

    @Test
    void getLastTimestamp() {
        assertEquals(200, clockTooManyBackwardsException.getLastTimestamp());
    }

    @Test
    void getCurrentTimestamp() {
        assertEquals(100, clockTooManyBackwardsException.getCurrentTimestamp());
    }

    @Test
    void getBrokenThreshold() {
        assertEquals(50, clockTooManyBackwardsException.getBrokenThreshold());
    }

    @Test
    void messageIncludesBackwardsContext() {
        assertEquals("Clock moved backwards too many.  brokenThreshold:[50] | lastTimestamp:[200] | currentTimestamp:[100]",
            clockTooManyBackwardsException.getMessage());
    }
}
