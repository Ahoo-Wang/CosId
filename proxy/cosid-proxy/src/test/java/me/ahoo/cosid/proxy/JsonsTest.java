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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import me.ahoo.cosid.proxy.api.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

class JsonsTest {
    @Test
    void roundTripErrorResponse() {
        ErrorResponse errorResponse = ErrorResponse.badRequest("validation failed", List.of("namespace required"));

        String jsonStr = Jsons.serialize(errorResponse);
        ErrorResponse actual = Jsons.deserialize(jsonStr, ErrorResponse.class);

        assertThat(actual.getCode(), equalTo(errorResponse.getCode()));
        assertThat(actual.getMsg(), equalTo(errorResponse.getMsg()));
        assertThat(actual.getErrors(), equalTo(errorResponse.getErrors()));
    }

    @Test
    void serializeOmitsNullValues() {
        ErrorResponse errorResponse = ErrorResponse.badRequest("badRequest");

        String jsonStr = Jsons.serialize(errorResponse);

        assertThat(jsonStr, containsString("\"code\":\"400\""));
        assertThat(jsonStr, containsString("\"msg\":\"badRequest\""));
        assertThat(jsonStr, not(containsString("errors")));
    }

    @Test
    void deserializeIgnoresUnknownProperties() {
        String content = "{\"code\":\"400\",\"msg\":\"badRequest\",\"unknown\":\"ignored\"}";

        ErrorResponse errorResponse = Jsons.deserialize(content, ErrorResponse.class);

        assertThat(errorResponse.getCode(), equalTo("400"));
        assertThat(errorResponse.getMsg(), equalTo("badRequest"));
        assertThat(errorResponse.getErrors(), nullValue());
    }
}
