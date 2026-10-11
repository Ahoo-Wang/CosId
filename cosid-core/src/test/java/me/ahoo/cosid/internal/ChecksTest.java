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

package me.ahoo.cosid.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChecksTest {
    
    @Test
    void lenientFormatMatchesGuava() {
        assertEquals("a 1 b 2", Checks.lenientFormat("a %s b %s", 1, 2));
        assertEquals("a 1 b %s", Checks.lenientFormat("a %s b %s", 1));
        assertEquals("a 1 [2, 3]", Checks.lenientFormat("a %s", 1, 2, 3));
        assertEquals("null", Checks.lenientFormat(null));
        assertEquals("no args", Checks.lenientFormat("no args"));
    }
    
    @Test
    void checksThrowWithTheMessage() {
        assertEquals("step:[0] must be greater than 0!",
            assertThrows(IllegalArgumentException.class, () -> Checks.checkArgument(false, "step:[%s] must be greater than 0!", 0)).getMessage());
        assertEquals("bad", assertThrows(IllegalStateException.class, () -> Checks.checkState(false, "bad")).getMessage());
        assertEquals("x can not be null!", assertThrows(NullPointerException.class, () -> Checks.checkNotNull(null, "x can not be null!")).getMessage());
        String value = "v";
        assertSame(value, Checks.checkNotNull(value, "unused"));
        Checks.checkArgument(true, "unused");
    }
    
    @Test
    void stringHelpers() {
        assertTrue(Checks.isNullOrEmpty(null));
        assertTrue(Checks.isNullOrEmpty(""));
        assertEquals("00000042", Checks.padStart("42", 8, '0'));
        assertEquals("123456789", Checks.padStart("123456789", 8, '0'));
        assertEquals("ChecksTest{a=1, b=x}", Checks.toString(this, "a", 1, "b", "x"));
    }
    
    @Test
    void daemonThreadFactoryNamesTheThread() {
        Thread thread = Checks.daemonThreadFactory("worker").newThread(() -> { });
        assertEquals("worker", thread.getName());
        assertTrue(thread.isDaemon());
    }
}
