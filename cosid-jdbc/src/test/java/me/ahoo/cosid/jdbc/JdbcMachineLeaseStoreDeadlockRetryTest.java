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
import static org.hamcrest.Matchers.instanceOf;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

/**
 * A call the database chooses as a deadlock victim runs again, a bounded number of times; other errors do not.
 */
class JdbcMachineLeaseStoreDeadlockRetryTest {
    private static final String TAKE = "update cosid_machine set instance_id=?, last_timestamp=?, distribute_time=?";
    private static final String INSERT = "insert into cosid_machine";
    private static final String RENEW = "update cosid_machine set last_timestamp=?";
    private static final long T0 = 1_000_000L;
    private static final int MAX_MACHINE_ID = 1023;

    private RecordingDataSource dataSource;
    private JdbcMachineLeaseStore store;
    private String namespace;

    @BeforeEach
    void setup() {
        dataSource = new RecordingDataSource(RealH2.createDataSource());
        store = DialectTables.leaseStore(dataSource, JdbcDialect.H2);
        namespace = "deadlock_" + MockIdGenerator.INSTANCE.generateAsString();
    }

    private static InstanceId instance(String name) {
        return InstanceId.of(name, false);
    }

    @Test
    void aReleasedClaimChosenAsDeadlockVictimRunsAgain() {
        store.release(store.claimNew(namespace, instance("owner"), MAX_MACHINE_ID, T0).orElseThrow(), T0 + 10);
        dataSource.failExecutions(TAKE, "40001", 1205, 2);

        Optional<Lease> lease = store.claimReleased(namespace, instance("claimer"), MAX_MACHINE_ID, T0 + 20);

        assertThat(lease.isPresent(), equalTo(true));
        assertThat(lease.get().getMachineId(), equalTo(0));
        assertThat(dataSource.getAttempts(), equalTo(3));
    }

    @Test
    void aNewClaimChosenAsDeadlockVictimRunsAgain() {
        dataSource.failExecutions(INSERT, "40P01", 0, 1);

        Optional<Lease> lease = store.claimNew(namespace, instance("claimer"), MAX_MACHINE_ID, T0);

        assertThat(lease.map(Lease::getMachineId), equalTo(Optional.of(0)));
        assertThat(dataSource.getAttempts(), equalTo(2));
    }

    @Test
    void aRenewalChosenAsDeadlockVictimRunsAgain() {
        Lease lease = store.claimNew(namespace, instance("owner"), MAX_MACHINE_ID, T0).orElseThrow();
        dataSource.failExecutions(RENEW, "40001", 1213, 1);

        assertThat(store.renew(lease, T0 + 10).isPresent(), equalTo(true));
        assertThat(dataSource.getAttempts(), equalTo(2));
    }

    @Test
    void aClaimGivesUpAfterTheLastDeadlockAttempt() {
        store.release(store.claimNew(namespace, instance("owner"), MAX_MACHINE_ID, T0).orElseThrow(), T0 + 10);
        dataSource.failExecutions(TAKE, "40001", 1205, JdbcMachineLeaseStore.MAX_DEADLOCK_ATTEMPTS);

        CosIdException exception = Assertions.assertThrows(CosIdException.class,
            () -> store.claimReleased(namespace, instance("claimer"), MAX_MACHINE_ID, T0 + 20));

        assertThat(exception.getCause(), instanceOf(SQLException.class));
        assertThat(((SQLException) exception.getCause()).getSQLState(), equalTo("40001"));
        assertThat(dataSource.getAttempts(), equalTo(JdbcMachineLeaseStore.MAX_DEADLOCK_ATTEMPTS));
        // Nothing was taken: the released machine id is still free.
        assertThat(store.claimReleased(namespace, instance("claimer"), MAX_MACHINE_ID, T0 + 30).map(Lease::getMachineId),
            equalTo(Optional.of(0)));
    }

    @Test
    void otherErrorsDoNotRunAgain() {
        store.release(store.claimNew(namespace, instance("owner"), MAX_MACHINE_ID, T0).orElseThrow(), T0 + 10);
        // A MySQL lock wait timeout, also vendor code 1205: retrying would wait as long again.
        dataSource.failExecutions(TAKE, "HY000", 1205, 1);

        Assertions.assertThrows(CosIdException.class,
            () -> store.claimReleased(namespace, instance("claimer"), MAX_MACHINE_ID, T0 + 20));
        assertThat(dataSource.getAttempts(), equalTo(1));
    }

    @Test
    void anInterruptedPauseGivesUpWithTheDeadlock() {
        store.release(store.claimNew(namespace, instance("owner"), MAX_MACHINE_ID, T0).orElseThrow(), T0 + 10);
        dataSource.failExecutions(TAKE, "40001", 1205, 1);

        Thread.currentThread().interrupt();
        try {
            CosIdException exception = Assertions.assertThrows(CosIdException.class,
                () -> store.claimReleased(namespace, instance("claimer"), MAX_MACHINE_ID, T0 + 20));
            assertThat(((SQLException) exception.getCause()).getSQLState(), equalTo("40001"));
            assertThat(Thread.currentThread().isInterrupted(), equalTo(true));
            assertThat(dataSource.getAttempts(), equalTo(1));
        } finally {
            Thread.interrupted();
        }
    }
}
