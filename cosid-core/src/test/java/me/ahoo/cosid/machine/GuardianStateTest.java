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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuardianStateTest {

    @Test
    void initialShouldRepresentNoGuardAttempt() {
        assertEquals(0L, GuardianState.INITIAL.getGuardAt());
        assertNull(GuardianState.INITIAL.getError());
        assertFalse(GuardianState.INITIAL.isFailed());
    }

    @Test
    void successShouldRecordGuardTimestampWithoutFailure() {
        GuardianState state = GuardianState.success(123L);

        assertEquals(123L, state.getGuardAt());
        assertNull(state.getError());
        assertFalse(state.isFailed());
    }

    @Test
    void failedShouldRecordOriginalThrowable() {
        IllegalStateException error = new IllegalStateException("guard failed");
        GuardianState state = GuardianState.failed(456L, error);

        assertEquals(456L, state.getGuardAt());
        assertSame(error, state.getError());
        assertTrue(state.isFailed());
    }

    @Test
    void equalityShouldIncludeTimestampAndThrowableReference() {
        RuntimeException error = new RuntimeException("same reference");
        GuardianState left = GuardianState.failed(1L, error);
        GuardianState same = GuardianState.failed(1L, error);
        GuardianState differentTimestamp = GuardianState.failed(2L, error);
        GuardianState differentError = GuardianState.failed(1L, new RuntimeException("same reference"));

        assertEquals(left, same);
        assertEquals(left.hashCode(), same.hashCode());
        assertNotEquals(left, differentTimestamp);
        assertNotEquals(left, differentError);
    }

    @Test
    void toStringShouldExposeGuardAtAndErrorFields() {
        GuardianState state = GuardianState.failed(789L, new IllegalArgumentException("bad guard"));

        String text = state.toString();

        assertTrue(text.contains("guardAt=789"));
        assertTrue(text.contains("error="));
        assertTrue(text.contains("bad guard"));
    }
}
