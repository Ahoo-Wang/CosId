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

package me.ahoo.cosid.zookeeper;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.KeeperException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ZookeeperMachineIdDistributorUnitTest {

    private static final String NAMESPACE = "ns";
    private static final String INSTANCE_IDX_PATH = "/cosid/ns/__itc_idx";
    private static final String LOST_INSTANCE_PATH = INSTANCE_IDX_PATH + "/lost";
    private static final String REUSABLE_INSTANCE_PATH = INSTANCE_IDX_PATH + "/reusable";
    private static final String CURRENT_INSTANCE_PATH = INSTANCE_IDX_PATH + "/current";
    private static final InstanceId CURRENT_INSTANCE_ID = InstanceId.of("current", false);
    private static final Object LOG_LEVEL_LOCK = new Object();

    @Test
    void distributeByRecyclableShouldContinueWhenCandidateDeletedBeforeRead() throws Exception {
        CuratorStub curatorStub = new CuratorStub(Failure.READ);
        ZookeeperMachineIdDistributor distributor = distributor(curatorStub);

        MachineState actual = distributor.distributeByRecyclable(NAMESPACE, CURRENT_INSTANCE_PATH, CURRENT_INSTANCE_ID, Duration.ZERO);

        Assertions.assertEquals(2, actual.getMachineId());
        Assertions.assertEquals(actual, MachineState.of(new String(curatorStub.data.get(CURRENT_INSTANCE_PATH), StandardCharsets.UTF_8)));
    }

    @Test
    void distributeByRecyclableShouldContinueWhenCandidateDeletedBeforeDelete() throws Exception {
        CuratorStub curatorStub = new CuratorStub(Failure.DELETE);
        ZookeeperMachineIdDistributor distributor = distributor(curatorStub);

        MachineState actual = distributor.distributeByRecyclable(NAMESPACE, CURRENT_INSTANCE_PATH, CURRENT_INSTANCE_ID, Duration.ZERO);

        Assertions.assertEquals(2, actual.getMachineId());
        Assertions.assertEquals(actual, MachineState.of(new String(curatorStub.data.get(CURRENT_INSTANCE_PATH), StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @EnumSource(Failure.class)
    void distributeByRecyclableShouldContinueWhenCandidateDeletedAndDebugDisabled(Failure failure) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ZookeeperMachineIdDistributor.class);
        synchronized (LOG_LEVEL_LOCK) {
            Level originalLevel = logger.getLevel();
            logger.setLevel(Level.INFO);
            try {
                Assertions.assertFalse(logger.isDebugEnabled());
                CuratorStub curatorStub = new CuratorStub(failure);
                ZookeeperMachineIdDistributor distributor = distributor(curatorStub);

                MachineState actual = distributor.distributeByRecyclable(
                    NAMESPACE, CURRENT_INSTANCE_PATH, CURRENT_INSTANCE_ID, Duration.ZERO);

                Assertions.assertEquals(2, actual.getMachineId());
                Assertions.assertEquals(actual, MachineState.of(new String(curatorStub.data.get(CURRENT_INSTANCE_PATH), StandardCharsets.UTF_8)));
            } finally {
                logger.setLevel(originalLevel);
            }
        }
    }

    private ZookeeperMachineIdDistributor distributor(CuratorStub curatorStub) {
        return new ZookeeperMachineIdDistributor(
            curatorStub.curatorFramework(),
            new RetryNTimes(1, 1),
            MachineStateStorage.IN_MEMORY,
            ClockBackwardsSynchronizer.DEFAULT
        );
    }

    private enum Failure {
        READ,
        DELETE
    }

    private static final class CuratorStub {
        private final Failure failure;
        private final Map<String, byte[]> data = new HashMap<>();

        private CuratorStub(Failure failure) {
            this.failure = failure;
            data.put(LOST_INSTANCE_PATH, MachineState.of(1, 1).toStateString().getBytes(StandardCharsets.UTF_8));
            data.put(REUSABLE_INSTANCE_PATH, MachineState.of(2, 1).toStateString().getBytes(StandardCharsets.UTF_8));
        }

        private CuratorFramework curatorFramework() {
            return proxy(CuratorFramework.class, (proxy, method, args) -> {
                String methodName = method.getName();
                if ("getChildren".equals(methodName)) {
                    return builder(method.getReturnType(), "children");
                }
                if ("getData".equals(methodName)) {
                    return builder(method.getReturnType(), "data");
                }
                if ("delete".equals(methodName)) {
                    return builder(method.getReturnType(), "delete");
                }
                if ("create".equals(methodName)) {
                    return builder(method.getReturnType(), "create");
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Object builder(Class<?> builderType, String operation) {
            return proxy(builderType, (proxy, method, args) -> {
                if ("forPath".equals(method.getName())) {
                    String path = (String) args[0];
                    return switch (operation) {
                        case "children" -> List.of("lost", "reusable");
                        case "data" -> getData(path);
                        case "delete" -> delete(path, method.getReturnType());
                        case "create" -> create(path, (byte[]) args[1]);
                        default -> defaultValue(method.getReturnType());
                    };
                }
                if (method.getReturnType().isInterface()) {
                    return builder(method.getReturnType(), operation);
                }
                return defaultValue(method.getReturnType());
            });
        }

        private byte[] getData(String path) throws KeeperException.NoNodeException {
            if (Failure.READ == failure && LOST_INSTANCE_PATH.equals(path)) {
                throw new KeeperException.NoNodeException(path);
            }
            byte[] state = data.get(path);
            if (state == null) {
                throw new KeeperException.NoNodeException(path);
            }
            return state;
        }

        private Object delete(String path, Class<?> returnType) throws KeeperException.NoNodeException {
            if (Failure.DELETE == failure && LOST_INSTANCE_PATH.equals(path)) {
                throw new KeeperException.NoNodeException(path);
            }
            data.remove(path);
            return defaultValue(returnType);
        }

        private String create(String path, byte[] state) {
            data.put(path, state);
            return path;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(proxy, args);
                };
            }
            return handler.invoke(proxy, method, args);
        });
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
