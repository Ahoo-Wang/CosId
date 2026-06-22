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

package me.ahoo.cosid.test.container;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class MongoLauncherTest {
    private static final String EXTERNAL_URI = "mongodb://example.com:27017/cosid";

    @AfterEach
    void clearProperty() {
        System.clearProperty(MongoLauncher.CONNECTION_STRING_PROPERTY);
    }

    @Test
    void resolveExplicitConnectionString() {
        assertThat(
            MongoLauncher.resolveExplicitConnectionString(" " + EXTERNAL_URI + " ", null),
            equalTo(Optional.of(EXTERNAL_URI))
        );
    }

    @Test
    void propertyOverridesEnvironment() {
        assertThat(
            MongoLauncher.resolveExplicitConnectionString("mongodb://property:27017/cosid", "mongodb://env:27017/cosid"),
            equalTo(Optional.of("mongodb://property:27017/cosid"))
        );
    }

    @Test
    void blankPropertyFallsBackToEnvironment() {
        assertThat(
            MongoLauncher.resolveExplicitConnectionString(" ", "mongodb://env:27017/cosid"),
            equalTo(Optional.of("mongodb://env:27017/cosid"))
        );
    }

    @Test
    void explicitConnectionStringBypassesContainer() {
        System.setProperty(MongoLauncher.CONNECTION_STRING_PROPERTY, EXTERNAL_URI);

        assertThat(MongoLauncher.getConnectionString(), equalTo(EXTERNAL_URI));
    }
}
