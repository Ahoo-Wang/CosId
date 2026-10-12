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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.test.machine.lease.MixedLeaseFormatSpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;

/**
 * Runs {@link MixedLeaseFormatSpec} on a real MySQL: {@link JdbcMachineIdDistributor} as the 3.4 instances.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMySqlMixedLeaseFormatTest extends MixedLeaseFormatSpec {
    DataSource dataSource;
    JdbcMachineLeaseStore store;
    
    @BeforeAll
    void setup() {
        dataSource = PooledDataSource.of(RealMySql.createDataSource(), true);
        store = new JdbcMachineLeaseStore(dataSource);
    }
    
    @Override
    protected MachineIdDistributor v34() {
        return new JdbcMachineIdDistributor(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected MachineIdDistributor v40() {
        return new LeaseStoreMachineIdDistributor(store, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
    }
}
