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
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

class JdbcMachineIdDistributorUnitTest {

    private static final String NAMESPACE = "ns";
    private static final InstanceId INSTANCE_ID = InstanceId.of("host", 1, false);

    @Test
    void distributeBySelfShouldReturnRemoteFutureTimestamp() {
        long futureLastTimestamp = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
        JdbcStub jdbcStub = JdbcStub.self(MachineState.of(7, futureLastTimestamp));
        TestableJdbcMachineIdDistributor distributor = new TestableJdbcMachineIdDistributor(jdbcStub.dataSource());

        MachineState actual = distributor.distributeRemoteForTest(NAMESPACE, 4, INSTANCE_ID, Duration.ZERO);

        Assertions.assertEquals(7, actual.getMachineId());
        Assertions.assertEquals(futureLastTimestamp, actual.getLastTimeStamp());
        Assertions.assertEquals(futureLastTimestamp, jdbcStub.guardedTimestamp);
    }

    @Test
    void distributeByRevertShouldTryNextCandidateWhenFirstUpdateLosesRace() throws Exception {
        MachineState first = MachineState.of(1, 10);
        MachineState second = MachineState.of(2, 20);
        JdbcStub jdbcStub = JdbcStub.revert(List.of(first, second), List.of(0, 1));
        JdbcMachineIdDistributor distributor = new JdbcMachineIdDistributor(jdbcStub.dataSource(), MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
        Method distributeByRevert = JdbcMachineIdDistributor.class.getDeclaredMethod("distributeByRevert",
            String.class, InstanceId.class, Connection.class, Duration.class);
        distributeByRevert.setAccessible(true);

        MachineState actual;
        try {
            actual = (MachineState) distributeByRevert.invoke(distributor, NAMESPACE, INSTANCE_ID, jdbcStub.connection(), Duration.ZERO);
        } catch (InvocationTargetException invocationException) {
            Throwable cause = invocationException.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw invocationException;
        }

        Assertions.assertEquals(second, actual);
    }

    private static final class TestableJdbcMachineIdDistributor extends JdbcMachineIdDistributor {
        private TestableJdbcMachineIdDistributor(DataSource dataSource) {
            super(dataSource, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT);
        }

        private MachineState distributeRemoteForTest(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
            return super.distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
        }
    }

    private static final class JdbcStub {
        private final List<MachineState> selfRows;
        private final List<MachineState> revertRows;
        private final Queue<Integer> revertUpdateResults;
        private Long guardedTimestamp;

        private JdbcStub(List<MachineState> selfRows, List<MachineState> revertRows, List<Integer> revertUpdateResults) {
            this.selfRows = selfRows;
            this.revertRows = revertRows;
            this.revertUpdateResults = new ArrayDeque<>(revertUpdateResults);
        }

        private static JdbcStub self(MachineState selfState) {
            return new JdbcStub(List.of(selfState), List.of(), List.of());
        }

        private static JdbcStub revert(List<MachineState> revertRows, List<Integer> revertUpdateResults) {
            return new JdbcStub(List.of(), revertRows, revertUpdateResults);
        }

        private DataSource dataSource() {
            return proxy(DataSource.class, (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return connection();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return preparedStatement((String) args[0]);
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement preparedStatement(String sql) {
            Map<Integer, Object> params = new HashMap<>();
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set")) {
                    params.put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeQuery".equals(name)) {
                    if (sql.contains("instance_id=? and last_timestamp>?")) {
                        return resultSet(selfRows);
                    }
                    if (sql.contains("instance_id='' or last_timestamp<=?")) {
                        return resultSet(revertRows);
                    }
                    return resultSet(List.of());
                }
                if ("executeUpdate".equals(name)) {
                    if (sql.contains("where name=? and (instance_id='' or last_timestamp<=?)")) {
                        return revertUpdateResults.remove();
                    }
                    if (sql.contains("set last_timestamp=?")) {
                        guardedTimestamp = (Long) params.get(1);
                        return 1;
                    }
                    return 1;
                }
                if ("close".equals(name)) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private ResultSet resultSet(List<MachineState> rows) {
            List<MachineState> rowList = new ArrayList<>(rows);
            return proxy(ResultSet.class, new InvocationHandler() {
                private int index = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> ++index < rowList.size();
                        case "getInt" -> rowList.get(index).getMachineId();
                        case "getLong" -> rowList.get(index).getLastTimeStamp();
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    };
                }
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType.equals(Boolean.TYPE)) {
                return false;
            }
            if (returnType.equals(Integer.TYPE)) {
                return 0;
            }
            if (returnType.equals(Long.TYPE)) {
                return 0L;
            }
            if (returnType.equals(Void.TYPE)) {
                return null;
            }
            return null;
        }
    }
}
