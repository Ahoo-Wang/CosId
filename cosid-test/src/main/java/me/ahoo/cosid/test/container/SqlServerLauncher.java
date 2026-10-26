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

import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

/**
 * Provides a real SQL Server for backend contract tests, on request only: the image is large.
 *
 * <p>Uses {@code cosid.test.sqlserver.url} / {@code COSID_TEST_SQLSERVER_URL} when set, together with
 * {@code cosid.test.sqlserver.username} / {@code COSID_TEST_SQLSERVER_USERNAME} (default {@code sa}) and
 * {@code cosid.test.sqlserver.password} / {@code COSID_TEST_SQLSERVER_PASSWORD}. Otherwise, when
 * {@code cosid.test.sqlserver.enabled} / {@code COSID_TEST_SQLSERVER_ENABLED} is {@code true}, starts a shared SQL Server
 * 2022 container (accepting its license). Tests should skip when {@link #isAvailable()} is false.</p>
 */
public final class SqlServerLauncher {
    public static final String URL_PROPERTY = "cosid.test.sqlserver.url";
    public static final String URL_ENV = "COSID_TEST_SQLSERVER_URL";
    public static final String USERNAME_PROPERTY = "cosid.test.sqlserver.username";
    public static final String USERNAME_ENV = "COSID_TEST_SQLSERVER_USERNAME";
    public static final String PASSWORD_PROPERTY = "cosid.test.sqlserver.password";
    public static final String PASSWORD_ENV = "COSID_TEST_SQLSERVER_PASSWORD";
    public static final String ENABLED_PROPERTY = "cosid.test.sqlserver.enabled";
    public static final String ENABLED_ENV = "COSID_TEST_SQLSERVER_ENABLED";
    private static final String DEFAULT_USERNAME = "sa";
    @SuppressWarnings("resource")
    private static final MSSQLServerContainer SQL_SERVER_CONTAINER =
        new MSSQLServerContainer(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest")).acceptLicense();
    
    private SqlServerLauncher() {
    }
    
    /**
     * Whether a SQL Server was asked for: an explicit URL, or the container enabled.
     *
     * @return true if {@link #getConnection()} can be called
     */
    public static boolean isAvailable() {
        return ExplicitSetting.resolve(URL_PROPERTY, URL_ENV).isPresent()
            || ExplicitSetting.resolve(ENABLED_PROPERTY, ENABLED_ENV).map(Boolean::parseBoolean).orElse(false);
    }
    
    public static Connection getConnection() {
        Optional<String> explicitUrl = ExplicitSetting.resolve(URL_PROPERTY, URL_ENV);
        if (explicitUrl.isPresent()) {
            return new Connection(
                explicitUrl.get(),
                ExplicitSetting.resolve(USERNAME_PROPERTY, USERNAME_ENV).orElse(DEFAULT_USERNAME),
                ExplicitSetting.resolve(PASSWORD_PROPERTY, PASSWORD_ENV).orElse("")
            );
        }
        SQL_SERVER_CONTAINER.start();
        return new Connection(SQL_SERVER_CONTAINER.getJdbcUrl(), SQL_SERVER_CONTAINER.getUsername(), SQL_SERVER_CONTAINER.getPassword());
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
