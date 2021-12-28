/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
 * @author ahoo wang
 */
@Slf4j
public class JdbcMachineIdInitializer {
    private final static String INIT_COSID_MACHINE_TABLE_SQL =
            "create table if not exists cosid_machine\n" +
                    "(\n" +
                    "    name            varchar(100) not null comment '{namespace}.{machine_id}',\n" +
                    "    namespace       varchar(100) not null,\n" +
                    "    machine_id      integer      not null default 0,\n" +
                    "    last_timestamp  bigint       not null default 0,\n" +
                    "    instance_id     varchar(100) not null default '',\n" +
                    "    distribute_time bigint       not null default 0,\n" +
                    "    revert_time     bigint       not null default 0,\n" +
                    "    constraint cosid_machine_pk\n" +
                    "        primary key (name)\n" +
                    ") engine = InnoDB;";

    private final static String INIT_NAMESPACE_IDX_SQL =
            "create index if not exists idx_namespace on cosid_machine (namespace);";

    private final static String INIT_INSTANCE_ID_IDX_SQL =
            "create index if not exists idx_instance_id on cosid_machine (instance_id);";

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
            log.info("initCosIdMachineTable");
        }

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement initStatement = connection.prepareStatement(initCosIdMachineTableSql)) {
                initStatement.executeUpdate();
            }
            try (PreparedStatement createNamespaceIdxStatement = connection.prepareStatement(initNamespaceIdxSql)) {
                createNamespaceIdxStatement.executeUpdate();
            }
            try (PreparedStatement createInstanceIdIdxStatement = connection.prepareStatement(initInstanceIdIdxSql)) {
                createInstanceIdIdxStatement.executeUpdate();
            }
        }
    }

    public boolean tryInitCosIdMachineTable() {
        try {
            initCosIdMachineTable();
            return true;
        } catch (Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info("tryInitCosIdTable failed.[{}]", throwable.getMessage());
            }
            return false;
        }
    }
}
