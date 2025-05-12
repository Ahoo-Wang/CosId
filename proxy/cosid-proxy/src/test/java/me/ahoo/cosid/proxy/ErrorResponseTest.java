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

package me.ahoo.cosid.proxy;

import static me.ahoo.cosid.proxy.ErrorResponse.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class ErrorResponseTest {
    @Test
    void badRequest() {
        ErrorResponse errorResponse = ErrorResponse.badRequest("badRequest");
        assertThat(errorResponse.getCode(), equalTo(BAD_REQUEST));
        assertThat(errorResponse.getMsg(), equalTo("badRequest"));
        assertThat(errorResponse.getErrors(), nullValue());
    }
    @Test
    void unknown() {
        ErrorResponse errorResponse = ErrorResponse.unknown("unknown");
        assertThat(errorResponse.getCode(), equalTo("500"));
        assertThat(errorResponse.getMsg(), equalTo("unknown"));
    }
}
