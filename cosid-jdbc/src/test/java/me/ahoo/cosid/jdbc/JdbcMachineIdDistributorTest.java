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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.Assert;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.machine.distributor.MachineIdDistributorSpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.time.Duration;

/**
 * @author ahoo wang
 */
class JdbcMachineIdDistributorTest extends MachineIdDistributorSpec {
    private static final String NAMESPACE = "JdbcMachineIdDistributorTest";
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", 1, false);

    InMemoryJdbcDataSource dataSource;
    JdbcMachineIdInitializer jdbcMachineIdInitializer;
    MachineStateStorage machineStateStorage;
    
    @BeforeEach
    void setup() {
        dataSource = DataSourceFactory.INSTANCE.createDataSource();
        jdbcMachineIdInitializer = new JdbcMachineIdInitializer(dataSource);
        machineStateStorage = new InMemoryMachineStateStorage();
    }
    
    @Test
    void tryInitCosIdMachineTable() {
        assertThat(jdbcMachineIdInitializer.tryInitCosIdMachineTable(), equalTo(true));
        assertThat(dataSource.isCosIdMachineTableInitialized(), equalTo(true));
    }
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new JdbcMachineIdDistributor(dataSource, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
    }

    @Test
    @Override
    public void guardLost() {
        MachineIdDistributor distributor = getDistributor();
        String namespace = MockIdGenerator.usePrefix("GuardLost").generateAsString();
        InstanceId instanceId = mockInstance(0, false);
        machineStateStorage.set(namespace, getMachineBit(), instanceId);

        Assert.assertThrows(MachineIdLostException.class, () -> {
            distributor.guard(namespace, instanceId, MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION);
        });
    }

    @Test
    void distributeBySelfShouldReturnRemoteFutureTimestamp() {
        long futureLastTimestamp = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        dataSource.putMachine(NAMESPACE, 7, INSTANCE_ID.getInstanceId(), futureLastTimestamp);
        TestableJdbcMachineIdDistributor distributor = new TestableJdbcMachineIdDistributor(dataSource);

        MachineState actual = distributor.distributeRemoteForTest(NAMESPACE, 4, INSTANCE_ID, Duration.ZERO);

        assertThat(actual.getMachineId(), equalTo(7));
        assertThat(actual.getLastTimeStamp(), equalTo(futureLastTimestamp));
        assertThat(dataSource.findMachine(NAMESPACE, 7).orElseThrow().lastTimestamp(), equalTo(futureLastTimestamp));
    }

    @Test
    void distributeByRevertShouldTryNextCandidateWhenFirstUpdateLosesRace() throws Exception {
        dataSource.putMachine(NAMESPACE, 1, "", 10);
        dataSource.putMachine(NAMESPACE, 2, "", 20);
        dataSource.failNextRevertDistributeUpdates(1);
        JdbcMachineIdDistributor distributor = new JdbcMachineIdDistributor(dataSource, machineStateStorage, ClockBackwardsSynchronizer.DEFAULT);
        Method distributeByRevert = JdbcMachineIdDistributor.class.getDeclaredMethod("distributeByRevert",
            String.class, InstanceId.class, Connection.class, Duration.class);
        distributeByRevert.setAccessible(true);

        MachineState actual;
        try (Connection connection = dataSource.getConnection()) {
            actual = (MachineState) distributeByRevert.invoke(distributor, NAMESPACE, INSTANCE_ID, connection, Duration.ZERO);
        } catch (InvocationTargetException invocationException) {
            Throwable cause = invocationException.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw invocationException;
        }

        assertThat(actual.getMachineId(), equalTo(2));
        assertThat(dataSource.findMachine(NAMESPACE, 1).orElseThrow().instanceId(), equalTo(""));
        assertThat(dataSource.findMachine(NAMESPACE, 2).orElseThrow().instanceId(), equalTo(INSTANCE_ID.getInstanceId()));
    }

    @Test
    void revertRemoteShouldReleaseUnstableInstanceForReuse() {
        dataSource.putMachine(NAMESPACE, 3, INSTANCE_ID.getInstanceId(), 1);
        TestableJdbcMachineIdDistributor distributor = new TestableJdbcMachineIdDistributor(dataSource);

        distributor.revertRemoteForTest(NAMESPACE, INSTANCE_ID, MachineState.of(3, 123));

        InMemoryJdbcDataSource.MachineRowSnapshot actual = dataSource.findMachine(NAMESPACE, 3).orElseThrow();
        assertThat(actual.instanceId(), equalTo(""));
        assertThat(actual.lastTimestamp(), equalTo(123L));
    }

    @Test
    void guardRemoteShouldUpdateRemoteTimestamp() {
        dataSource.putMachine(NAMESPACE, 4, INSTANCE_ID.getInstanceId(), 1);
        TestableJdbcMachineIdDistributor distributor = new TestableJdbcMachineIdDistributor(dataSource);

        distributor.guardRemoteForTest(NAMESPACE, INSTANCE_ID, MachineState.of(4, 456), Duration.ZERO);

        assertThat(dataSource.findMachine(NAMESPACE, 4).orElseThrow().lastTimestamp(), equalTo(456L));
    }

    private static final class TestableJdbcMachineIdDistributor extends JdbcMachineIdDistributor {
        private TestableJdbcMachineIdDistributor(InMemoryJdbcDataSource dataSource) {
            super(dataSource, new InMemoryMachineStateStorage(), ClockBackwardsSynchronizer.DEFAULT);
        }

        private MachineState distributeRemoteForTest(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            return super.distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
        }

        private void revertRemoteForTest(String namespace, InstanceId instanceId, MachineState machineState) {
            super.revertRemote(namespace, instanceId, machineState);
        }

        private void guardRemoteForTest(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
            super.guardRemote(namespace, instanceId, machineState, safeGuardDuration);
        }
    }
    
}
