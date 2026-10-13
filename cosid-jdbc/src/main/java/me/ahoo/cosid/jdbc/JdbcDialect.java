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

import me.ahoo.cosid.internal.Checks;

import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * The SQL of each supported database: the {@code cosid} and {@code cosid_machine} DDL, the segment statements and the
 * server clock query.
 *
 * <p>{@link #of(String)} picks one from the JDBC database product name; the starter does so when the SQL properties are
 * left at their defaults. Oracle and other databases are not supported: configure every statement for them.</p>
 *
 * @author ahoo wang
 * @since 4.1.0
 */
public enum JdbcDialect {
    /**
     * MySQL and MariaDB: the defaults of {@link JdbcIdSegmentInitializer}, {@link JdbcIdSegmentDistributor},
     * {@link JdbcMachineIdInitializer} and {@link JdbcMachineLeaseStore}.
     */
    MYSQL(JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL, JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL,
        JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL, JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL, null,
        JdbcMachineIdInitializer.INIT_COSID_MACHINE_TABLE_SQL, JdbcMachineIdInitializer.INIT_NAMESPACE_IDX_SQL,
        JdbcMachineIdInitializer.INIT_INSTANCE_ID_IDX_SQL, JdbcMachineLeaseStore.ADD_LEASE_COLUMNS_SQL, JdbcMachineLeaseStore.MYSQL_SERVER_TIME_SQL),
    /**
     * PostgreSQL: {@link PostgreSqlDialect}.
     */
    POSTGRESQL(PostgreSqlDialect.INIT_COSID_TABLE_SQL, PostgreSqlDialect.INIT_ID_SEGMENT_SQL,
        PostgreSqlDialect.INCREMENT_MAX_ID_SQL, PostgreSqlDialect.FETCH_MAX_ID_SQL, PostgreSqlDialect.INCREMENT_AND_FETCH_MAX_ID_SQL,
        PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL, PostgreSqlDialect.INIT_NAMESPACE_IDX_SQL,
        PostgreSqlDialect.INIT_INSTANCE_ID_IDX_SQL, PostgreSqlDialect.ADD_LEASE_COLUMNS_SQL, PostgreSqlDialect.SERVER_TIME_SQL),
    /**
     * H2 2.x, in any compatibility mode.
     */
    H2(H2Sql.INIT_COSID_TABLE_SQL, H2Sql.INIT_ID_SEGMENT_SQL, H2Sql.INCREMENT_MAX_ID_SQL, PostgreSqlDialect.FETCH_MAX_ID_SQL,
        H2Sql.INCREMENT_AND_FETCH_MAX_ID_SQL, PostgreSqlDialect.INIT_COSID_MACHINE_TABLE_SQL, PostgreSqlDialect.INIT_NAMESPACE_IDX_SQL,
        PostgreSqlDialect.INIT_INSTANCE_ID_IDX_SQL, H2Sql.ADD_LEASE_COLUMNS_SQL, H2Sql.SERVER_TIME_SQL),
    /**
     * SQL Server 2016 and later.
     */
    SQL_SERVER(SqlServerSql.INIT_COSID_TABLE_SQL, SqlServerSql.INIT_ID_SEGMENT_SQL, SqlServerSql.INCREMENT_MAX_ID_SQL,
        PostgreSqlDialect.FETCH_MAX_ID_SQL, SqlServerSql.INCREMENT_AND_FETCH_MAX_ID_SQL, SqlServerSql.INIT_COSID_MACHINE_TABLE_SQL,
        SqlServerSql.INIT_NAMESPACE_IDX_SQL, SqlServerSql.INIT_INSTANCE_ID_IDX_SQL, SqlServerSql.ADD_LEASE_COLUMNS_SQL, SqlServerSql.SERVER_TIME_SQL);
    
    private final String initCosIdTableSql;
    private final String initIdSegmentSql;
    private final String incrementMaxIdSql;
    private final String fetchMaxIdSql;
    private final @Nullable String incrementAndFetchMaxIdSql;
    private final String initCosIdMachineTableSql;
    private final String initNamespaceIdxSql;
    private final String initInstanceIdIdxSql;
    private final String addLeaseColumnsSql;
    private final String serverTimeSql;
    
    JdbcDialect(String initCosIdTableSql, String initIdSegmentSql, String incrementMaxIdSql, String fetchMaxIdSql,
                @Nullable String incrementAndFetchMaxIdSql, String initCosIdMachineTableSql, String initNamespaceIdxSql,
                String initInstanceIdIdxSql, String addLeaseColumnsSql, String serverTimeSql) {
        this.initCosIdTableSql = initCosIdTableSql;
        this.initIdSegmentSql = initIdSegmentSql;
        this.incrementMaxIdSql = incrementMaxIdSql;
        this.fetchMaxIdSql = fetchMaxIdSql;
        this.incrementAndFetchMaxIdSql = incrementAndFetchMaxIdSql;
        this.initCosIdMachineTableSql = initCosIdMachineTableSql;
        this.initNamespaceIdxSql = initNamespaceIdxSql;
        this.initInstanceIdIdxSql = initInstanceIdIdxSql;
        this.addLeaseColumnsSql = addLeaseColumnsSql;
        this.serverTimeSql = serverTimeSql;
    }
    
    /**
     * The dialect of a JDBC database product name ({@link java.sql.DatabaseMetaData#getDatabaseProductName()}).
     *
     * @return empty for an unsupported database
     */
    public static Optional<JdbcDialect> of(@Nullable String databaseProductName) {
        if (databaseProductName == null) {
            return Optional.empty();
        }
        String product = databaseProductName.toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            return Optional.of(MYSQL);
        }
        if (product.contains("postgres")) {
            return Optional.of(POSTGRESQL);
        }
        if (product.equals("h2")) {
            return Optional.of(H2);
        }
        if (product.contains("sql server")) {
            return Optional.of(SQL_SERVER);
        }
        return Optional.empty();
    }
    
    /**
     * The dialect of the database behind {@code dataSource}, read from one connection's metadata.
     *
     * @return empty for an unsupported database
     * @throws SQLException when no connection can be opened
     */
    public static Optional<JdbcDialect> of(DataSource dataSource) throws SQLException {
        Checks.checkNotNull(dataSource, "dataSource can not be null!");
        try (Connection connection = dataSource.getConnection()) {
            return of(connection.getMetaData().getDatabaseProductName());
        }
    }
    
    /**
     * The dialect whose built-in segment statements these are, whatever their whitespace or final semicolon.
     */
    static Optional<JdbcDialect> ofSegmentStatements(String incrementMaxIdSql, String fetchMaxIdSql) {
        for (JdbcDialect dialect : values()) {
            if (JdbcIdSegmentDistributor.FetchPlan.sameSql(incrementMaxIdSql, dialect.incrementMaxIdSql)
                && JdbcIdSegmentDistributor.FetchPlan.sameSql(fetchMaxIdSql, dialect.fetchMaxIdSql)) {
                return Optional.of(dialect);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Creates the {@code cosid} table if it does not exist.
     */
    public String getInitCosIdTableSql() {
        return initCosIdTableSql;
    }
    
    /**
     * Inserts a segment row: the name, then the offset.
     */
    public String getInitIdSegmentSql() {
        return initIdSegmentSql;
    }
    
    /**
     * Adds the step to a segment: the step, then the name.
     */
    public String getIncrementMaxIdSql() {
        return incrementMaxIdSql;
    }
    
    /**
     * Reads a segment's max id: the name.
     */
    public String getFetchMaxIdSql() {
        return fetchMaxIdSql;
    }
    
    /**
     * {@link #getIncrementMaxIdSql()} and {@link #getFetchMaxIdSql()} in one query, which the distributor runs when it is
     * configured with those two; {@code null} for MySQL, which uses {@code last_insert_id()}.
     */
    public @Nullable String getIncrementAndFetchMaxIdSql() {
        return incrementAndFetchMaxIdSql;
    }
    
    /**
     * Creates the {@code cosid_machine} table if it does not exist.
     */
    public String getInitCosIdMachineTableSql() {
        return initCosIdMachineTableSql;
    }
    
    /**
     * Creates the {@code idx_namespace} index if it does not exist; empty where the table DDL declares it.
     */
    public String getInitNamespaceIdxSql() {
        return initNamespaceIdxSql;
    }
    
    /**
     * Creates the {@code idx_instance_id} index if it does not exist; empty where the table DDL declares it.
     */
    public String getInitInstanceIdIdxSql() {
        return initInstanceIdIdxSql;
    }
    
    /**
     * Adds the 4.0 lease columns to a {@code cosid_machine} table created before 4.0.
     */
    public String getAddLeaseColumnsSql() {
        return addLeaseColumnsSql;
    }
    
    /**
     * The server's current time in milliseconds, for {@link JdbcMachineLeaseStore}.
     */
    public String getServerTimeSql() {
        return serverTimeSql;
    }
    
    private static final class H2Sql {
        static final String EPOCH_SECONDS = "cast(floor(extract(epoch from current_timestamp)) as bigint)";
        static final String INIT_COSID_TABLE_SQL = PostgreSqlDialect.INIT_COSID_TABLE_SQL;
        static final String INIT_ID_SEGMENT_SQL = "insert into cosid (name, last_max_id, last_fetch_time) values (?, ?, " + EPOCH_SECONDS + ")";
        static final String INCREMENT_MAX_ID_SQL = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=" + EPOCH_SECONDS + " where name = ?";
        static final String INCREMENT_AND_FETCH_MAX_ID_SQL = "select last_max_id from final table (" + INCREMENT_MAX_ID_SQL + ")";
        static final String ADD_LEASE_COLUMNS_SQL = "alter table cosid_machine add column (renew_time bigint not null default 0, "
            + "watermark bigint not null default 0, lease_version bigint not null default 0)";
        static final String SERVER_TIME_SQL = "select cast(floor(extract(epoch from current_timestamp(3)) * 1000) as bigint)";
    }
    
    private static final class SqlServerSql {
        static final String EPOCH_SECONDS = "datediff_big(second, '19700101', sysutcdatetime())";
        static final String INIT_COSID_TABLE_SQL =
            "if object_id(N'cosid', N'U') is null\n"
                + "create table cosid\n"
                + "(\n"
                + "    name            varchar(100) not null,\n"
                + "    last_max_id     bigint       not null default 0,\n"
                + "    last_fetch_time bigint       not null default 0,\n"
                + "    constraint cosid_pk primary key (name)\n"
                + ")";
        static final String INIT_ID_SEGMENT_SQL = "insert into cosid (name, last_max_id, last_fetch_time) values (?, ?, " + EPOCH_SECONDS + ")";
        static final String INCREMENT_MAX_ID_SQL = "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=" + EPOCH_SECONDS + " where name = ?";
        static final String INCREMENT_AND_FETCH_MAX_ID_SQL =
            "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=" + EPOCH_SECONDS + " output inserted.last_max_id where name = ?";
        static final String INIT_COSID_MACHINE_TABLE_SQL =
            "if object_id(N'cosid_machine', N'U') is null\n"
                + "create table cosid_machine\n"
                + "(\n"
                + "    name            varchar(100) not null,\n"
                + "    namespace       varchar(100) not null,\n"
                + "    machine_id      integer      not null default 0,\n"
                + "    last_timestamp  bigint       not null default 0,\n"
                + "    instance_id     varchar(100) not null default '',\n"
                + "    distribute_time bigint       not null default 0,\n"
                + "    revert_time     bigint       not null default 0,\n"
                + "    stable          smallint     not null default 0,\n"
                + "    renew_time      bigint       not null default 0,\n"
                + "    watermark       bigint       not null default 0,\n"
                + "    lease_version   bigint       not null default 0,\n"
                + "    constraint cosid_machine_pk primary key (name)\n"
                + ")";
        static final String INIT_NAMESPACE_IDX_SQL = "if not exists (select 1 from sys.indexes where name = 'idx_namespace' "
            + "and object_id = object_id(N'cosid_machine')) create index idx_namespace on cosid_machine (namespace)";
        static final String INIT_INSTANCE_ID_IDX_SQL = "if not exists (select 1 from sys.indexes where name = 'idx_instance_id' "
            + "and object_id = object_id(N'cosid_machine')) create index idx_instance_id on cosid_machine (instance_id)";
        static final String ADD_LEASE_COLUMNS_SQL = "alter table cosid_machine add renew_time bigint not null default 0, "
            + "watermark bigint not null default 0, lease_version bigint not null default 0";
        static final String SERVER_TIME_SQL = "select datediff_big(millisecond, '19700101', sysutcdatetime())";
    }
}
