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
import me.ahoo.cosid.machine.InMemoryMachineStateStorage;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.test.MockIdGenerator;

import lombok.SneakyThrows;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concurrency scenarios of {@link ZookeeperMachineIdDistributor} that need a real ZooKeeper to reproduce races deterministically.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZookeeperMachineIdDistributorRaceTest {
    private static final int MACHINE_BIT = 1;
    private static final Duration SAFE_GUARD_DURATION = Duration.ofMinutes(1);
    private final RetryPolicy retryPolicy = new RetryNTimes(1, 10);
    private TestingServer testingServer;
    private CuratorFramework curatorFramework;

    @SneakyThrows
    @BeforeAll
    void setup() {
        testingServer = new TestingServer();
        curatorFramework = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), retryPolicy);
        curatorFramework.start();
        Assertions.assertTrue(curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS));
    }

    @SneakyThrows
    @AfterAll
    void destroy() {
        curatorFramework.close();
        testingServer.close();
    }

    private ZookeeperMachineIdDistributor distributor(CuratorFramework curator, MachineStateStorage storage) {
        return new ZookeeperMachineIdDistributor(curator, retryPolicy, storage, ClockBackwardsSynchronizer.DEFAULT);
    }

    private static String instancePath(String namespace, InstanceId instanceId) {
        return "/cosid/" + namespace + "/__itc_idx/" + instanceId.getInstanceId();
    }

    private static String revertPath(String namespace) {
        return "/cosid/" + namespace + "/__revert";
    }

    @SneakyThrows
    private MachineState readState(String path) {
        return MachineState.of(new String(curatorFramework.getData().forPath(path), StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private void writeState(String path, MachineState state) {
        curatorFramework.setData().forPath(path, state.toStateString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Fill every machine id of the namespace and make the first owner look expired.
     */
    private InstanceId fillNamespaceWithExpiredOwner(String namespace) {
        ZookeeperMachineIdDistributor distributor = distributor(curatorFramework, new InMemoryMachineStateStorage());
        InstanceId expiredOwner = InstanceId.of("expired-owner", false);
        InstanceId liveOwner = InstanceId.of("live-owner", false);
        Assertions.assertEquals(0, distributor.distribute(namespace, MACHINE_BIT, expiredOwner, SAFE_GUARD_DURATION).getMachineId());
        Assertions.assertEquals(1, distributor.distribute(namespace, MACHINE_BIT, liveOwner, SAFE_GUARD_DURATION).getMachineId());
        writeState(instancePath(namespace, expiredOwner), MachineState.of(0, 1));
        return expiredOwner;
    }

    @Test
    void recycleExpiredMachineId() {
        String namespace = MockIdGenerator.usePrefix("recycleExpired").generateAsString();
        InstanceId expiredOwner = fillNamespaceWithExpiredOwner(namespace);
        InstanceId newcomer = InstanceId.of("newcomer", false);

        MachineState actual = distributor(curatorFramework, new InMemoryMachineStateStorage())
            .distribute(namespace, MACHINE_BIT, newcomer, SAFE_GUARD_DURATION);

        Assertions.assertEquals(0, actual.getMachineId());
        Assertions.assertEquals(0, readState(instancePath(namespace, newcomer)).getMachineId());
        MachineStateStorage expiredOwnerStorage = new InMemoryMachineStateStorage();
        expiredOwnerStorage.set(namespace, 0, expiredOwner);
        Assertions.assertThrows(MachineIdLostException.class,
            () -> distributor(curatorFramework, expiredOwnerStorage).guard(namespace, expiredOwner, SAFE_GUARD_DURATION));
    }

    @Test
    void recycleMustNotTakeOverMachineIdGuardedAfterRead() {
        String namespace = MockIdGenerator.usePrefix("recycleGuarded").generateAsString();
        InstanceId expiredOwner = fillNamespaceWithExpiredOwner(namespace);
        String expiredOwnerPath = instancePath(namespace, expiredOwner);
        // The owner renews its lease right after the newcomer read the stale state.
        CuratorFramework racingCurator = afterRead(curatorFramework, expiredOwnerPath,
            () -> writeState(expiredOwnerPath, MachineState.of(0, System.currentTimeMillis())));
        InstanceId newcomer = InstanceId.of("newcomer", false);

        Assertions.assertThrows(MachineIdOverflowException.class,
            () -> distributor(racingCurator, new InMemoryMachineStateStorage()).distribute(namespace, MACHINE_BIT, newcomer, SAFE_GUARD_DURATION));

        Assertions.assertEquals(0, readState(expiredOwnerPath).getMachineId());
    }

    @Test
    @SneakyThrows
    void revertTemporaryMustNotReleaseLostMachineId() {
        String namespace = MockIdGenerator.usePrefix("revertLost").generateAsString();
        MachineStateStorage storage = new InMemoryMachineStateStorage();
        ZookeeperMachineIdDistributor distributor = distributor(curatorFramework, storage);
        InstanceId instanceId = InstanceId.of("lost", false);
        distributor.distribute(namespace, MACHINE_BIT, instanceId, SAFE_GUARD_DURATION);
        // Another instance recycled the machine id.
        curatorFramework.delete().forPath(instancePath(namespace, instanceId));

        distributor.revert(namespace, instanceId);

        Assertions.assertNull(curatorFramework.checkExists().forPath(revertPath(namespace) + "/0"));
    }

    @Test
    @SneakyThrows
    void revertTemporaryMovesMachineIdToRevertPool() {
        String namespace = MockIdGenerator.usePrefix("revertTemporary").generateAsString();
        ZookeeperMachineIdDistributor distributor = distributor(curatorFramework, new InMemoryMachineStateStorage());
        InstanceId instanceId = InstanceId.of("temporary", false);
        distributor.distribute(namespace, MACHINE_BIT, instanceId, SAFE_GUARD_DURATION);

        distributor.revert(namespace, instanceId);

        Assertions.assertNull(curatorFramework.checkExists().forPath(instancePath(namespace, instanceId)));
        Assertions.assertEquals(0, readState(revertPath(namespace) + "/0").getMachineId());
    }

    @Test
    @SneakyThrows
    void revertStableMustNotRecreateRecycledInstance() {
        String namespace = MockIdGenerator.usePrefix("revertStableLost").generateAsString();
        ZookeeperMachineIdDistributor distributor = distributor(curatorFramework, new InMemoryMachineStateStorage());
        InstanceId instanceId = InstanceId.of("stable", true);
        distributor.distribute(namespace, MACHINE_BIT, instanceId, SAFE_GUARD_DURATION);
        curatorFramework.delete().forPath(instancePath(namespace, instanceId));

        distributor.revert(namespace, instanceId);

        Assertions.assertNull(curatorFramework.checkExists().forPath(instancePath(namespace, instanceId)));
    }

    @Test
    void overflowReportsTotalMachineIds() {
        String namespace = MockIdGenerator.usePrefix("overflow").generateAsString();
        ZookeeperMachineIdDistributor distributor = distributor(curatorFramework, new InMemoryMachineStateStorage());
        distributor.distribute(namespace, MACHINE_BIT, InstanceId.of("a", false), SAFE_GUARD_DURATION);
        distributor.distribute(namespace, MACHINE_BIT, InstanceId.of("b", false), SAFE_GUARD_DURATION);

        MachineIdOverflowException exception = Assertions.assertThrows(MachineIdOverflowException.class,
            () -> distributor.distribute(namespace, MACHINE_BIT, InstanceId.of("c", false), SAFE_GUARD_DURATION));

        Assertions.assertEquals(2, exception.getTotalMachineIds());
    }

    /**
     * Wrap a {@link CuratorFramework} so that {@code hook} runs once, right after {@code getData()...forPath(targetPath)} returns.
     */
    private static CuratorFramework afterRead(CuratorFramework actual, String targetPath, Runnable hook) {
        AtomicBoolean fired = new AtomicBoolean(false);
        return delegate(CuratorFramework.class, actual, (method, result, args) -> {
            if ("getData".equals(method.getName())) {
                return wrapBuilder(result, targetPath, hook, fired);
            }
            return result;
        });
    }

    private static Object wrapBuilder(Object builder, String targetPath, Runnable hook, AtomicBoolean fired) {
        return Proxy.newProxyInstance(ZookeeperMachineIdDistributorRaceTest.class.getClassLoader(), allInterfaces(builder.getClass()), (proxy, method, args) -> {
            Object result = invoke(method, builder, args);
            if ("forPath".equals(method.getName())) {
                if (targetPath.equals(args[0]) && fired.compareAndSet(false, true)) {
                    hook.run();
                }
                return result;
            }
            if (result != null && allInterfaces(result.getClass()).length > 0) {
                return wrapBuilder(result, targetPath, hook, fired);
            }
            return result;
        });
    }

    private static Class<?>[] allInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> each : current.getInterfaces()) {
                collect(each, interfaces);
            }
        }
        return interfaces.stream().filter(each -> Modifier.isPublic(each.getModifiers())).toArray(Class<?>[]::new);
    }

    private static void collect(Class<?> type, Set<Class<?>> interfaces) {
        if (interfaces.add(type)) {
            for (Class<?> each : type.getInterfaces()) {
                collect(each, interfaces);
            }
        }
    }

    @FunctionalInterface
    private interface ResultInterceptor {
        Object intercept(Method method, Object result, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T delegate(Class<T> type, T actual, ResultInterceptor interceptor) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> interceptor.intercept(method, invoke(method, actual, args), args));
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException invocationTargetException) {
            throw invocationTargetException.getCause();
        }
    }
}
