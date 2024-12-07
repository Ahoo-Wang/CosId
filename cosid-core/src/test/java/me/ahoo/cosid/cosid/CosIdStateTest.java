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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosIdStateTest {

    @Test
    void testEquals() {
        CosIdState cosIdState = new CosIdState(0, 0, 0);
        assertEquals(cosIdState, cosIdState);
        assertNotEquals(this, cosIdState);
        CosIdState cosIdState1 = new CosIdState(0, 0, 0);
        assertEquals(cosIdState, cosIdState1);
        CosIdState cosIdState2 = new CosIdState(1, 0, 0);
        assertNotEquals(cosIdState, cosIdState2);
        CosIdState cosIdState3 = new CosIdState(0, 1, 0);
        assertNotEquals(cosIdState, cosIdState3);
        CosIdState cosIdState4 = new CosIdState(0, 0, 1);
        assertNotEquals(cosIdState, cosIdState4);
    }

    @Test
    void testHashCode() {
        CosIdState cosIdState = new CosIdState(0, 0, 0);
        assertEquals(cosIdState.hashCode(), cosIdState.hashCode());
        CosIdState cosIdState1 = new CosIdState(0, 0, 0);
        assertEquals(cosIdState.hashCode(), cosIdState1.hashCode());
        CosIdState cosIdState2 = new CosIdState(1, 0, 0);
        assertNotEquals(cosIdState.hashCode(), cosIdState2.hashCode());
        CosIdState cosIdState3 = new CosIdState(0, 1, 0);
        assertNotEquals(cosIdState.hashCode(), cosIdState3.hashCode());
        CosIdState cosIdState4 = new CosIdState(0, 0, 1);
        assertNotEquals(cosIdState.hashCode(), cosIdState4.hashCode());
    }

    @Test
    void testToString() {
        CosIdState cosIdState = new CosIdState(0, 0, 0);
        assertEquals("CosIdState{timestamp=0, machineId=0, sequence=0}", cosIdState.toString());
    }
}