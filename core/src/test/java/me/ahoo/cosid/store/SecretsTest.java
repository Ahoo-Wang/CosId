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

package me.ahoo.cosid.store;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import me.ahoo.cosid.runtime.RuntimeSpec;
import me.ahoo.cosid.runtime.StoreSpec;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The value types that carry store options print them without their secrets (R2-core-02): passwords, tokens and the
 * password of a URL.
 */
class SecretsTest {
    private static Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("url", "jdbc:mysql://cosid:s3cret@db:3306/cosid?ssl=true&password=s3cret");
        options.put("password", "s3cret");
        options.put("api_token", "s3cret");
        options.put("clientSecret", "s3cret");
        options.put("rangeTable", "ids_range");
        options.put("timeoutMillis", 500);
        return options;
    }

    @Test
    void theToStringOfStoreOptionsHidesTheirSecrets() {
        StoreConfig config = StoreConfig.builder("db", "jdbc").timeout(Duration.ofSeconds(2)).options(options()).build();
        StoreSpec spec = StoreSpec.builder("jdbc").timeout(Duration.ofSeconds(2)).options(options()).build();
        RuntimeSpec runtime = RuntimeSpec.builder("app").store("db", spec).build();
        for (String text : new String[] {config.toString(), spec.toString(), runtime.toString()}) {
            assertThat(text, not(containsString("s3cret")));
            assertThat(text, containsString("jdbc:mysql://cosid:***@db:3306/cosid?ssl=true&password=***"));
            assertThat(text, containsString("password=***"));
            assertThat(text, containsString("rangeTable=ids_range"));
            assertThat(text, containsString("timeoutMillis=500"));
        }
        // Only the text: the values stay as they are.
        assertThat(config.options().get("password"), is("s3cret"));
        assertThat(spec.options().get("api_token"), is("s3cret"));
    }
}
