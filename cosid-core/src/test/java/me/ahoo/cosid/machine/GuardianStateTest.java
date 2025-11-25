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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GuardianState}.
 */
class GuardianStateTest {

    @Test
    void testInitialState() {
        assertThat(GuardianState.INITIAL.getGuardAt(), equalTo(0L));
        assertThat(GuardianState.INITIAL.getError(), nullValue());
        assertThat(GuardianState.INITIAL.isFailed(), equalTo(false));
    }

    @Test
    void testSuccessState() {
        long guardAt = System.currentTimeMillis();
        GuardianState state = GuardianState.success(guardAt);

        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.getError(), nullValue());
        assertThat(state.isFailed(), equalTo(false));
    }

    @Test
    void testFailedState() {
        long guardAt = System.currentTimeMillis();
        RuntimeException error = new RuntimeException("Test error");
        GuardianState state = GuardianState.failed(guardAt, error);

        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.getError(), equalTo(error));
        assertThat(state.isFailed(), equalTo(true));
    }

    @Test
    void testConstructorWithNullError() {
        long guardAt = System.currentTimeMillis();
        GuardianState state = new GuardianState(guardAt, null);

        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.getError(), nullValue());
        assertThat(state.isFailed(), equalTo(false));
    }

    @Test
    void testConstructorWithError() {
        long guardAt = System.currentTimeMillis();
        IllegalArgumentException error = new IllegalArgumentException("Invalid argument");
        GuardianState state = new GuardianState(guardAt, error);

        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.getError(), equalTo(error));
        assertThat(state.isFailed(), equalTo(true));
    }

    @Test
    void testNegativeGuardAt() {
        long guardAt = -1L;
        GuardianState state = new GuardianState(guardAt, null);

        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.isFailed(), equalTo(false));
    }

    @Test
    void testZeroGuardAt() {
        GuardianState state = new GuardianState(0L, null);

        assertThat(state.getGuardAt(), equalTo(0L));
        assertThat(state.isFailed(), equalTo(false));
    }

    @Test
    void testLargeGuardAt() {
        long guardAt = Long.MAX_VALUE;
        GuardianState state = new GuardianState(guardAt, null);

        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.isFailed(), equalTo(false));
    }

    @Test
    void testDifferentErrorTypes() {
        long guardAt = System.currentTimeMillis();

        // Test with different exception types
        Exception exception = new Exception("General exception");
        GuardianState state1 = new GuardianState(guardAt, exception);
        assertThat(state1.isFailed(), equalTo(true));

        Error error = new Error("Error type");
        GuardianState state2 = new GuardianState(guardAt, error);
        assertThat(state2.isFailed(), equalTo(true));

        Throwable throwable = new Throwable("Throwable");
        GuardianState state3 = new GuardianState(guardAt, throwable);
        assertThat(state3.isFailed(), equalTo(true));
    }

    @Test
    void testEqualsAndHashCode() {
        long guardAt = System.currentTimeMillis();
        RuntimeException error = new RuntimeException("Test");

        GuardianState state1 = new GuardianState(guardAt, error);
        GuardianState state2 = new GuardianState(guardAt, error);
        GuardianState state3 = new GuardianState(guardAt + 1, error);
        GuardianState state4 = new GuardianState(guardAt, null);

        assertThat(state1, equalTo(state2));
        assertThat(state1.hashCode(), equalTo(state2.hashCode()));

        assertThat(state1, not(equalTo(state3)));
        assertThat(state1, not(equalTo(state4)));
        assertThat(state4, not(equalTo(state1)));
    }

    @Test
    void testToString() {
        long guardAt = 123456789L;
        GuardianState successState = new GuardianState(guardAt, null);
        GuardianState failedState = new GuardianState(guardAt, new RuntimeException("Test"));

        String successString = successState.toString();
        String failedString = failedState.toString();

        assertThat(successString, not(nullValue()));
        assertThat(failedString, not(nullValue()));
        assertThat(successString.contains("123456789"), equalTo(true));
        assertThat(failedString.contains("123456789"), equalTo(true));
    }

    @Test
    void testImmutability() {
        long guardAt = System.currentTimeMillis();
        RuntimeException error = new RuntimeException("Test");

        GuardianState state = new GuardianState(guardAt, error);

        // Attempting to modify should not affect the object (though Lombok @Data doesn't prevent it)
        // But since fields are final, they can't be modified
        assertThat(state.getGuardAt(), equalTo(guardAt));
        assertThat(state.getError(), equalTo(error));
    }

    @Test
    void testSuccessFactoryWithDifferentTimestamps() {
        long time1 = 1000L;
        long time2 = 2000L;

        GuardianState state1 = GuardianState.success(time1);
        GuardianState state2 = GuardianState.success(time2);

        assertThat(state1.getGuardAt(), equalTo(time1));
        assertThat(state2.getGuardAt(), equalTo(time2));
        assertThat(state1.isFailed(), equalTo(false));
        assertThat(state2.isFailed(), equalTo(false));
    }

    @Test
    void testFailedFactoryWithDifferentErrors() {
        long guardAt = System.currentTimeMillis();
        RuntimeException error1 = new RuntimeException("Error 1");
        IllegalStateException error2 = new IllegalStateException("Error 2");

        GuardianState state1 = GuardianState.failed(guardAt, error1);
        GuardianState state2 = GuardianState.failed(guardAt, error2);

        assertThat(state1.getError(), equalTo(error1));
        assertThat(state2.getError(), equalTo(error2));
        assertThat(state1.isFailed(), equalTo(true));
        assertThat(state2.isFailed(), equalTo(true));
    }
}