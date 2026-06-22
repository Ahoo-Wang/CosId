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

package me.ahoo.cosid;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class CosIdExceptionTest {
    private static final String MESSAGE = "cosId exception";
    private static final IllegalStateException CAUSE = new IllegalStateException("root cause");

    @Test
    void defaultConstructorShouldCreateExceptionWithoutMessageOrCause() {
        CosIdException exception = new CosIdException();

        assertThat(exception.getMessage(), nullValue());
        assertThat(exception.getCause(), nullValue());
    }

    @Test
    void messageConstructorShouldExposeDetailMessage() {
        CosIdException exception = new CosIdException(MESSAGE);

        assertThat(exception.getMessage(), equalTo(MESSAGE));
        assertThat(exception.getCause(), nullValue());
    }

    @Test
    void messageAndCauseConstructorShouldExposeBothValues() {
        CosIdException exception = new CosIdException(MESSAGE, CAUSE);

        assertThat(exception.getMessage(), equalTo(MESSAGE));
        assertThat(exception.getCause(), sameInstance(CAUSE));
    }

    @Test
    void causeConstructorShouldDelegateRuntimeExceptionMessageContract() {
        CosIdException exception = new CosIdException(CAUSE);

        assertThat(exception.getCause(), sameInstance(CAUSE));
        assertThat(exception.getMessage(), containsString(CAUSE.getClass().getName()));
        assertThat(exception.getMessage(), containsString(CAUSE.getMessage()));
    }

    @Test
    void fullConstructorShouldRespectSuppressionAndWritableStackTraceFlags() {
        CosIdException exception = new CosIdException(MESSAGE, CAUSE, false, false);

        exception.addSuppressed(new RuntimeException("suppressed"));

        assertThat(exception.getMessage(), equalTo(MESSAGE));
        assertThat(exception.getCause(), sameInstance(CAUSE));
        assertThat(exception.getSuppressed(), emptyArray());
        assertThat(exception.getStackTrace(), emptyArray());
    }
}
