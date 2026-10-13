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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * The CosId tables and segment factory of a {@link JdbcDialect}, for the real-database contract tests.
 */
final class DialectTables {
    private DialectTables() {
    }
    
    static DataSource create(DataSource dataSource, JdbcDialect dialect) throws SQLException {
        new JdbcIdSegmentInitializer(dialect.getInitCosIdTableSql(), dialect.getInitIdSegmentSql(), dataSource).initCosIdTable();
        new JdbcMachineIdInitializer(dataSource, dialect.getInitCosIdMachineTableSql(), dialect.getInitNamespaceIdxSql(),
            dialect.getInitInstanceIdIdxSql()).initCosIdMachineTable();
        return dataSource;
    }
    
    static JdbcIdSegmentDistributorFactory segmentDistributorFactory(DataSource dataSource, JdbcDialect dialect) {
        return new JdbcIdSegmentDistributorFactory(dataSource, true,
            new JdbcIdSegmentInitializer(dialect.getInitCosIdTableSql(), dialect.getInitIdSegmentSql(), dataSource),
            dialect.getIncrementMaxIdSql(), dialect.getFetchMaxIdSql());
    }
    
    static JdbcMachineLeaseStore leaseStore(DataSource dataSource, JdbcDialect dialect) {
        return new JdbcMachineLeaseStore(PooledDataSource.of(dataSource, true), JdbcSupport.DEFAULT_QUERY_TIMEOUT, dialect.getServerTimeSql());
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
