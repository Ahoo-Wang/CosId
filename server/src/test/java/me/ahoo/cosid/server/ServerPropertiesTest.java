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

package me.ahoo.cosid.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class ServerPropertiesTest {
    private static ServerProperties.Limits limits(Duration minTtl, Duration maxTtl, Duration maxTolerance, long maxReserve, int maxKeys) {
        return new ServerProperties.Limits(minTtl, maxTtl, maxTolerance, 1024, maxReserve, 100, maxKeys);
    }

    @Test
    void rejectsLimitsOutOfRange() {
        Duration second = Duration.ofSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> limits(Duration.ZERO, second, second, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> limits(second, Duration.ofMillis(10), second, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> limits(second, second, Duration.ofSeconds(-1), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> limits(second, second, second, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> limits(second, second, second, 1, -1));
        assertEquals(0, limits(second, second, second, 1, 0).maxKeysPerNamespace());
    }

    @Test
    void rejectsABlockingPoolOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new ServerProperties.Blocking(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ServerProperties.Blocking(1, -1));
        assertEquals(0, new ServerProperties.Blocking(1, 0).queueCapacity());
    }

    @Test
    void keyCapOfZeroAdmitsEveryKey() {
        KeyCap cap = new KeyCap(0, new Rejections(null));
        for (int i = 0; i < 100; i++) {
            cap.admit("ns/" + i);
        }
    }
}
