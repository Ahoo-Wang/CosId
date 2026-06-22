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

package me.ahoo.cosid.cosid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CosIdStateTest {

    @Test
    void accessorsShouldReturnConstructorComponents() {
        CosIdState state = new CosIdState(1000, 7, 42);

        assertEquals(1000, state.getTimestamp());
        assertEquals(7, state.getMachineId());
        assertEquals(42, state.getSequence());
    }

    @Test
    void equalityAndHashCodeShouldUseAllStateComponents() {
        CosIdState state = new CosIdState(1000, 7, 42);

        assertEquals(state, new CosIdState(1000, 7, 42));
        assertEquals(state.hashCode(), new CosIdState(1000, 7, 42).hashCode());
        assertNotEquals(state, new CosIdState(1001, 7, 42));
        assertNotEquals(state, new CosIdState(1000, 8, 42));
        assertNotEquals(state, new CosIdState(1000, 7, 43));
        assertNotEquals(state, "1000-7-42");
    }

    @Test
    void compareToShouldOrderByEncodedStateComponents() {
        CosIdState state = new CosIdState(1000, 7, 42);

        assertTrue(state.compareTo(new CosIdState(999, 1024, 65535)) > 0);
        assertTrue(state.compareTo(new CosIdState(1001, 0, 0)) < 0);
        assertTrue(state.compareTo(new CosIdState(1000, 6, 65535)) > 0);
        assertTrue(state.compareTo(new CosIdState(1000, 8, 0)) < 0);
        assertTrue(state.compareTo(new CosIdState(1000, 7, 41)) > 0);
        assertTrue(state.compareTo(new CosIdState(1000, 7, 43)) < 0);
        assertEquals(0, state.compareTo(new CosIdState(1000, 7, 42)));
    }

    @Test
    void toStringShouldExposeAllComponentsForDiagnostics() {
        CosIdState state = new CosIdState(1000, 7, 42);

        assertEquals("CosIdState{timestamp=1000, machineId=7, sequence=42}", state.toString());
    }
}
