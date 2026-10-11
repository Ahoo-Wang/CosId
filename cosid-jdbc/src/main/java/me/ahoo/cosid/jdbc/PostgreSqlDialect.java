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

/**
 * PostgreSQL versions of the SQL that defaults to MySQL syntax.
 *
 * <p>The machine id distributor's statements are standard SQL and need no change; the DDL and the segment statements
 * use MySQL-only syntax ({@code unsigned}, {@code engine}, {@code unix_timestamp()}). Pass these to
 * {@link JdbcIdSegmentInitializer}, {@link JdbcIdSegmentDistributorFactory} and {@link JdbcMachineIdInitializer}, or to the
 * matching {@code cosid.segment.distributor.jdbc.*} and {@code cosid.machine.distributor.jdbc.*} properties.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public final class PostgreSqlDialect {
    private static final String NOW_EPOCH_SECONDS = "cast(extract(epoch from now()) as bigint)";
    
    public static final String INIT_COSID_TABLE_SQL =
        "create table if not exists cosid\n"
            + "(\n"
            + "    name            varchar(100) not null,\n"
            + "    last_max_id     bigint       not null default 0,\n"
            + "    last_fetch_time bigint       not null default 0,\n"
            + "    constraint cosid_pk primary key (name)\n"
            + ")";
    
    public static final String INIT_ID_SEGMENT_SQL =
        "insert into cosid (name, last_max_id, last_fetch_time) values (?, ?, " + NOW_EPOCH_SECONDS + ")";
    
    public static final String INCREMENT_MAX_ID_SQL =
        "update cosid set last_max_id=(last_max_id + ?),last_fetch_time=" + NOW_EPOCH_SECONDS + " where name = ?";
    
    public static final String FETCH_MAX_ID_SQL = "select last_max_id from cosid where name = ?";
    
    public static final String INIT_COSID_MACHINE_TABLE_SQL =
        "create table if not exists cosid_machine\n"
            + "(\n"
            + "    name            varchar(100) not null,\n"
            + "    namespace       varchar(100) not null,\n"
            + "    machine_id      integer      not null default 0,\n"
            + "    last_timestamp  bigint       not null default 0,\n"
            + "    instance_id     varchar(100) not null default '',\n"
            + "    distribute_time bigint       not null default 0,\n"
            + "    revert_time     bigint       not null default 0,\n"
            + "    stable          smallint     not null default 0,\n"
            + "    constraint cosid_machine_pk primary key (name)\n"
            + ")";
    
    public static final String INIT_NAMESPACE_IDX_SQL = "create index if not exists idx_namespace on cosid_machine (namespace)";
    
    public static final String INIT_INSTANCE_ID_IDX_SQL = "create index if not exists idx_instance_id on cosid_machine (instance_id)";
    
    /**
     * Migration of a {@code cosid_machine} table created before the {@code stable} column existed.
     */
    public static final String ADD_STABLE_COLUMN_SQL = "alter table cosid_machine add column if not exists stable smallint not null default 0";
    
    /**
     * Current time of the server in milliseconds, for {@link JdbcMachineLeaseStore}.
     */
    public static final String SERVER_TIME_SQL = "select cast(extract(epoch from clock_timestamp()) * 1000 as bigint)";
    
    private PostgreSqlDialect() {
    }
}
