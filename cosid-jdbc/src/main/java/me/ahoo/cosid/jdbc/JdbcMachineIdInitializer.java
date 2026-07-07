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

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Jdbc MachineId Initializer.
 *
 * @author ahoo wang
 */
@Slf4j
public class JdbcMachineIdInitializer {
    /**
     * Default SQL for creating the {@code cosid_machine} table.
     *
     * <p>The {@code idx_namespace} and {@code idx_instance_id} indexes are declared inline so the
     * table and its indexes are created by a single MySQL-compatible statement. MySQL does not
     * support {@code CREATE INDEX IF NOT EXISTS}, so the index DDLs default to empty strings and
     * are skipped by {@link #initCosIdMachineTable()}.</p>
     */
    public static final String INIT_COSID_MACHINE_TABLE_SQL =
        "create table if not exists cosid_machine\n"
            + "(\n"
            + "    name            varchar(100)     not null comment '{namespace}.{machine_id}',\n"
            + "    namespace       varchar(100)     not null,\n"
            + "    machine_id      integer unsigned not null default 0,\n"
            + "    last_timestamp  bigint unsigned  not null default 0,\n"
            + "    instance_id     varchar(100)     not null default '',\n"
            + "    distribute_time bigint unsigned  not null default 0,\n"
            + "    revert_time     bigint unsigned  not null default 0,\n"
            + "    constraint cosid_machine_pk\n"
            + "        primary key (name),\n"
            + "    key idx_namespace (namespace),\n"
            + "    key idx_instance_id (instance_id)\n"
            + ") engine = InnoDB;";

    /**
     * Default SQL for creating the {@code idx_namespace} index on {@code cosid_machine}.
     *
     * <p>Empty by default: the index is created inline by {@link #INIT_COSID_MACHINE_TABLE_SQL}.
     * Set this to a non-empty value (e.g. {@code CREATE INDEX ...}) to create the index separately;
     * empty values are skipped by {@link #initCosIdMachineTable()}.</p>
     */
    public static final String INIT_NAMESPACE_IDX_SQL = "";

    /**
     * Default SQL for creating the {@code idx_instance_id} index on {@code cosid_machine}.
     *
     * <p>Empty by default: the index is created inline by {@link #INIT_COSID_MACHINE_TABLE_SQL}.
     * Set this to a non-empty value (e.g. {@code CREATE INDEX ...}) to create the index separately;
     * empty values are skipped by {@link #initCosIdMachineTable()}.</p>
     */
    public static final String INIT_INSTANCE_ID_IDX_SQL = "";
    
    private final DataSource dataSource;
    
    private final String initCosIdMachineTableSql;
    private final String initNamespaceIdxSql;
    private final String initInstanceIdIdxSql;
    
    public JdbcMachineIdInitializer(DataSource dataSource) {
        this(dataSource, INIT_COSID_MACHINE_TABLE_SQL, INIT_NAMESPACE_IDX_SQL, INIT_INSTANCE_ID_IDX_SQL);
    }
    
    public JdbcMachineIdInitializer(DataSource dataSource, String initCosIdMachineTableSql, String initNamespaceIdxSql, String initInstanceIdIdxSql) {
        this.dataSource = dataSource;
        this.initCosIdMachineTableSql = initCosIdMachineTableSql;
        this.initNamespaceIdxSql = initNamespaceIdxSql;
        this.initInstanceIdIdxSql = initInstanceIdIdxSql;
    }
    
    public void initCosIdMachineTable() throws SQLException {
        if (log.isInfoEnabled()) {
            log.info("Init CosIdMachineTable");
        }

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement initStatement = connection.prepareStatement(initCosIdMachineTableSql)) {
                initStatement.executeUpdate();
            }
            executeIfPresent(connection, initNamespaceIdxSql);
            executeIfPresent(connection, initInstanceIdIdxSql);
        }
    }

    /**
     * Executes the given SQL only when it is non-empty, so that index DDLs which are empty by
     * default (created inline by the table DDL) can be skipped without failing on empty statements.
     *
     * @param connection the JDBC connection to use
     * @param sql the SQL to execute; no-op when null or blank
     * @throws SQLException if executing the statement fails
     */
    private void executeIfPresent(Connection connection, String sql) throws SQLException {
        if (sql == null || sql.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
    
    public boolean tryInitCosIdMachineTable() {
        try {
            initCosIdMachineTable();
            return true;
        } catch (Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info("Try Init CosIdMachineTable failed.[{}]", throwable.getMessage());
            }
            return false;
        }
    }
}
