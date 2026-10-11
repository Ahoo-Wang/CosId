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

class ExplicitSettingTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty(RedisLauncher.URI_PROPERTY);
        System.clearProperty(MySqlLauncher.URL_PROPERTY);
        System.clearProperty(MySqlLauncher.USERNAME_PROPERTY);
        System.clearProperty(MySqlLauncher.PASSWORD_PROPERTY);
    }

    @Test
    void propertyOverridesEnvironment() {
        assertThat(ExplicitSetting.firstNonBlank(" property ", "env"), equalTo(Optional.of("property")));
    }

    @Test
    void blankPropertyFallsBackToEnvironment() {
        assertThat(ExplicitSetting.firstNonBlank(" ", "env"), equalTo(Optional.of("env")));
        assertThat(ExplicitSetting.firstNonBlank(null, " "), equalTo(Optional.empty()));
    }

    @Test
    void explicitRedisUriBypassesContainer() {
        System.setProperty(RedisLauncher.URI_PROPERTY, "redis://example.com:6379");

        assertThat(RedisLauncher.getUri(), equalTo("redis://example.com:6379"));
    }

    @Test
    void explicitMySqlUrlBypassesContainer() {
        System.setProperty(MySqlLauncher.URL_PROPERTY, "jdbc:mysql://example.com:3306/cosid_db");
        System.setProperty(MySqlLauncher.PASSWORD_PROPERTY, "secret");

        assertThat(MySqlLauncher.getConnection(),
            equalTo(new MySqlLauncher.Connection("jdbc:mysql://example.com:3306/cosid_db", "root", "secret")));
    }
}
