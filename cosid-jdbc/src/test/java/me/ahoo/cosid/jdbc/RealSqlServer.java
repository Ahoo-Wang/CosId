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

package me.ahoo.cosid.jdbc;

import me.ahoo.cosid.test.container.SqlServerLauncher;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assumptions;

import javax.sql.DataSource;

/**
 * A {@link DataSource} on the SQL Server of {@link SqlServerLauncher}, with the CosId tables of
 * {@link JdbcDialect#SQL_SERVER}.
 */
final class RealSqlServer {
    private RealSqlServer() {
    }
    
    /**
     * Skips the calling test class unless a SQL Server was asked for ({@code COSID_TEST_SQLSERVER_URL} or
     * {@code COSID_TEST_SQLSERVER_ENABLED=true}).
     */
    @SneakyThrows
    static DataSource createDataSource() {
        Assumptions.assumeTrue(SqlServerLauncher.isAvailable(), "SQL Server tests need " + SqlServerLauncher.URL_ENV + " or "
            + SqlServerLauncher.ENABLED_ENV + "=true");
        SqlServerLauncher.Connection connection = SqlServerLauncher.getConnection();
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setURL(connection.jdbcUrl());
        dataSource.setUser(connection.username());
        dataSource.setPassword(connection.password());
        return DialectTables.create(dataSource, JdbcDialect.SQL_SERVER);
    }
}
