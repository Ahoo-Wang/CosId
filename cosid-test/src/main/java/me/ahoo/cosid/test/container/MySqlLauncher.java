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

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

/**
 * Provides a real MySQL for backend contract tests.
 *
 * <p>Uses {@code cosid.test.mysql.url} / {@code COSID_TEST_MYSQL_URL} when set, together with
 * {@code cosid.test.mysql.username} / {@code COSID_TEST_MYSQL_USERNAME} and
 * {@code cosid.test.mysql.password} / {@code COSID_TEST_MYSQL_PASSWORD} (both default to {@code root}).
 * Otherwise starts a shared MySQL container with an empty {@code cosid_db} database.
 */
public final class MySqlLauncher {
    public static final String URL_PROPERTY = "cosid.test.mysql.url";
    public static final String URL_ENV = "COSID_TEST_MYSQL_URL";
    public static final String USERNAME_PROPERTY = "cosid.test.mysql.username";
    public static final String USERNAME_ENV = "COSID_TEST_MYSQL_USERNAME";
    public static final String PASSWORD_PROPERTY = "cosid.test.mysql.password";
    public static final String PASSWORD_ENV = "COSID_TEST_MYSQL_PASSWORD";
    private static final String DEFAULT_CREDENTIAL = "root";
    @SuppressWarnings("resource")
    private static final MySQLContainer MYSQL_CONTAINER = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
        .withDatabaseName("cosid_db");

    private MySqlLauncher() {
    }

    public static Connection getConnection() {
        Optional<String> explicitUrl = ExplicitSetting.resolve(URL_PROPERTY, URL_ENV);
        if (explicitUrl.isPresent()) {
            return new Connection(
                explicitUrl.get(),
                ExplicitSetting.resolve(USERNAME_PROPERTY, USERNAME_ENV).orElse(DEFAULT_CREDENTIAL),
                ExplicitSetting.resolve(PASSWORD_PROPERTY, PASSWORD_ENV).orElse(DEFAULT_CREDENTIAL)
            );
        }
        MYSQL_CONTAINER.start();
        return new Connection(MYSQL_CONTAINER.getJdbcUrl(), MYSQL_CONTAINER.getUsername(), MYSQL_CONTAINER.getPassword());
    }

    /**
     * JDBC connection settings.
     *
     * @param jdbcUrl  jdbc url
     * @param username username
     * @param password password
     */
    public record Connection(String jdbcUrl, String username, String password) {
    }
}
