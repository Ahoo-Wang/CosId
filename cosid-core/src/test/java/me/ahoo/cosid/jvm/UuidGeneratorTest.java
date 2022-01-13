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

package me.ahoo.cosid.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class UuidGeneratorTest {

    @Test
    void generate() {
        assertThrows(UnsupportedOperationException.class, () -> {
            UuidGenerator.INSTANCE.generate();
        });
    }

    @Test
    void generateAsString() {
        String uuid = UuidGenerator.INSTANCE.generateAsString();
        assertNotNull(uuid);
        assertEquals(36, uuid.length());
    }
}
