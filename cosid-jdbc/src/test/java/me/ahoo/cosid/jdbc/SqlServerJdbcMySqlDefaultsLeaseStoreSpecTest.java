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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.test.machine.lease.MachineLeaseStoreSpec;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;

/**
 * Runs {@link MachineLeaseStoreSpec} on a real SQL Server with the MySQL default DDL and server time query, which the
 * initializer and the store replace by those of {@link JdbcDialect#SQL_SERVER}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerJdbcMySqlDefaultsLeaseStoreSpecTest extends MachineLeaseStoreSpec {
    JdbcMachineLeaseStore store;

    @SneakyThrows
    @BeforeAll
    void setup() {
        DataSource dataSource = RealSqlServer.createEmptyDataSource();
        new JdbcMachineIdInitializer(dataSource).initCosIdMachineTable();
        store = new JdbcMachineLeaseStore(PooledDataSource.of(dataSource, true));
        assertThat(store.getServerTimeSql(), equalTo(JdbcDialect.SQL_SERVER.getServerTimeSql()));
    }

    @Override
    protected MachineLeaseStore getStore() {
        return store;
    }
}
