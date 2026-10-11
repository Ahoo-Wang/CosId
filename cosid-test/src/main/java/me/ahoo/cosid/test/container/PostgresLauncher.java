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

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

/**
 * Provides a real PostgreSQL for backend contract tests.
 *
 * <p>Uses {@code cosid.test.postgres.url} / {@code COSID_TEST_POSTGRES_URL} when set, together with
 * {@code cosid.test.postgres.username} / {@code COSID_TEST_POSTGRES_USERNAME} and
 * {@code cosid.test.postgres.password} / {@code COSID_TEST_POSTGRES_PASSWORD} (both default to {@code postgres}).
 * Otherwise starts a shared PostgreSQL 16 container with an empty {@code cosid_db} database.
 * Tests should skip when {@link #isAvailable()} is false.
 */
public final class PostgresLauncher {
    public static final String URL_PROPERTY = "cosid.test.postgres.url";
    public static final String URL_ENV = "COSID_TEST_POSTGRES_URL";
    public static final String USERNAME_PROPERTY = "cosid.test.postgres.username";
    public static final String USERNAME_ENV = "COSID_TEST_POSTGRES_USERNAME";
    public static final String PASSWORD_PROPERTY = "cosid.test.postgres.password";
    public static final String PASSWORD_ENV = "COSID_TEST_POSTGRES_PASSWORD";
    private static final String DEFAULT_CREDENTIAL = "postgres";
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("cosid_db");

    private PostgresLauncher() {
    }

    /**
     * Whether a PostgreSQL can be provided: an explicit URL is set or Docker is available.
     *
     * @return true if {@link #getConnection()} can succeed
     */
    public static boolean isAvailable() {
        return ExplicitSetting.resolve(URL_PROPERTY, URL_ENV).isPresent() || DockerClientFactory.instance().isDockerAvailable();
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
        POSTGRES_CONTAINER.start();
        return new Connection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
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
