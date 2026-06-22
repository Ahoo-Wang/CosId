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

package me.ahoo.cosid.proxy.api;

import static me.ahoo.cosid.proxy.api.ErrorResponse.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ErrorResponseTest {
    @Test
    void ofCreatesResponseWithEmptyErrors() {
        ErrorResponse errorResponse = ErrorResponse.of("M-01", "machine overflow");

        assertThat(errorResponse.getCode(), equalTo("M-01"));
        assertThat(errorResponse.getMsg(), equalTo("machine overflow"));
        assertThat(errorResponse.getErrors(), empty());
    }

    @Test
    void badRequestWithMessageLeavesErrorsAbsent() {
        ErrorResponse errorResponse = ErrorResponse.badRequest("invalid request");

        assertThat(errorResponse.getCode(), equalTo(BAD_REQUEST));
        assertThat(errorResponse.getMsg(), equalTo("invalid request"));
        assertThat(errorResponse.getErrors(), nullValue());
    }

    @Test
    void badRequestWithErrorsLeavesMessageAbsent() {
        Map<String, String> fieldError = Map.of("field", "namespace", "message", "must not be blank");

        ErrorResponse errorResponse = ErrorResponse.badRequest(List.of(fieldError));

        assertThat(errorResponse.getCode(), equalTo(BAD_REQUEST));
        assertThat(errorResponse.getMsg(), nullValue());
        assertThat(errorResponse.getErrors(), contains(fieldError));
    }

    @Test
    void badRequestWithMessageAndErrorsPreservesBoth() {
        List<String> errors = List.of("namespace must not be blank", "machineBit must be positive");

        ErrorResponse errorResponse = ErrorResponse.badRequest("validation failed", errors);

        assertThat(errorResponse.getCode(), equalTo(BAD_REQUEST));
        assertThat(errorResponse.getMsg(), equalTo("validation failed"));
        assertThat(errorResponse.getErrors(), equalTo(errors));
    }

    @Test
    void unknownUsesInternalErrorCode() {
        ErrorResponse errorResponse = ErrorResponse.unknown("unexpected failure");

        assertThat(errorResponse.getCode(), equalTo("500"));
        assertThat(errorResponse.getMsg(), equalTo("unexpected failure"));
        assertThat(errorResponse.getErrors(), empty());
    }
}
