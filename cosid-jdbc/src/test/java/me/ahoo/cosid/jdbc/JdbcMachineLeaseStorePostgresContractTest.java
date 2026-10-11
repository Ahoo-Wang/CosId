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

import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.test.machine.lease.MachineLeaseStoreSpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;

/**
 * Runs {@link MachineLeaseStoreSpec} against a real PostgreSQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMachineLeaseStorePostgresContractTest extends MachineLeaseStoreSpec {
    JdbcMachineLeaseStore store;
    
    @BeforeAll
    void setup() {
        DataSource dataSource = PooledDataSource.of(RealPostgres.createDataSource(), true);
        store = new JdbcMachineLeaseStore(dataSource, JdbcSupport.DEFAULT_QUERY_TIMEOUT, PostgreSqlDialect.SERVER_TIME_SQL);
    }
    
    @Override
    protected MachineLeaseStore getStore() {
        return store;
    }
    
    @Override
    protected boolean separatesWatermark() {
        // Still the 3.4 format: one last timestamp for the watermark and the renewal.
        return false;
    }
}
