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

import me.ahoo.cosid.test.container.PostgresLauncher;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assumptions;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Builds a {@link DataSource} connected to the real PostgreSQL provided by {@link PostgresLauncher}, with CosId tables
 * created from {@link PostgreSqlDialect}.
 */
final class RealPostgres {
    
    private RealPostgres() {
    }
    
    /**
     * Skips the calling test class when neither {@code COSID_TEST_POSTGRES_URL} nor Docker is available.
     */
    @SneakyThrows
    static DataSource createDataSource() {
        Assumptions.assumeTrue(PostgresLauncher.isAvailable(), "PostgreSQL contract tests need Docker or " + PostgresLauncher.URL_ENV);
        PostgresLauncher.Connection connection = PostgresLauncher.getConnection();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(connection.jdbcUrl());
        dataSource.setUser(connection.username());
        dataSource.setPassword(connection.password());
        new JdbcIdSegmentInitializer(PostgreSqlDialect.INIT_COSID_TABLE_SQL, PostgreSqlDialect.INIT_ID_SEGMENT_SQL, dataSource).initCosIdTable();
        new JdbcMachineIdInitializer(dataSource, PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL, PostgreSqlDialect.INIT_NAMESPACE_IDX_SQL,
            PostgreSqlDialect.INIT_INSTANCE_ID_IDX_SQL).initCosIdMachineTable();
        return dataSource;
    }
    
    static JdbcIdSegmentDistributorFactory segmentDistributorFactory(DataSource dataSource) {
        return new JdbcIdSegmentDistributorFactory(dataSource, true,
            new JdbcIdSegmentInitializer(PostgreSqlDialect.INIT_COSID_TABLE_SQL, PostgreSqlDialect.INIT_ID_SEGMENT_SQL, dataSource),
            PostgreSqlDialect.INCREMENT_MAX_ID_SQL, PostgreSqlDialect.FETCH_MAX_ID_SQL);
    }
    
    static void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
    
    static void setSegmentMaxId(DataSource dataSource, String segmentName, long maxId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("update cosid set last_max_id = ? where name = ?")) {
            statement.setLong(1, maxId);
            statement.setString(2, segmentName);
            statement.executeUpdate();
        }
    }
}
