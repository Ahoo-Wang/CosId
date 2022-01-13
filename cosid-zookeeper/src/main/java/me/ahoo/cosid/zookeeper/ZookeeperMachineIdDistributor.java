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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.MachineIdOverflowException;
import me.ahoo.cosid.snowflake.machine.MachineState;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.util.Exceptions;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
@Slf4j
public class ZookeeperMachineIdDistributor extends AbstractMachineIdDistributor {

    /**
     * /cosid/{namespace}/__itc_idx/{instanceId}
     * data:{@link MachineState#toStateString()}
     */
    private static final String INSTANCE_IDX_PATH = "__itc_idx";
    /**
     * /cosid/{namespace}/__revert/{machineId}
     * data:lastStamp
     */
    private static final String REVERT_PATH = "__revert";

    private final CuratorFramework curatorFramework;
    private final RetryPolicy retryPolicy;

    public ZookeeperMachineIdDistributor(CuratorFramework curatorFramework, RetryPolicy retryPolicy, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.curatorFramework = curatorFramework;
        this.retryPolicy = retryPolicy;
    }

    /**
     * /cosid/{namespace}/__counter
     *
     * @param namespace namespace of app
     * @return path of counter
     */
    private static String getCounterPath(String namespace) {
        return Strings.lenientFormat("/%s/%s/%s", CosId.COSID, namespace, "__counter");
    }

    private static String getCounterLockerPath(String namespace) {
        return Strings.lenientFormat("%s-locker", getCounterPath(namespace));
    }

    private static String getInstanceIdxPath(String namespace) {
        return Strings.lenientFormat("/%s/%s/%s", CosId.COSID, namespace, INSTANCE_IDX_PATH);
    }

    private static String getInstancePath(String namespace, String instanceId) {
        return Strings.lenientFormat("%s/%s", getInstanceIdxPath(namespace), instanceId);
    }

    private static String getRevertPath(String namespace) {
        return Strings.lenientFormat("/%s/%s/%s", CosId.COSID, namespace, REVERT_PATH);
    }

    private static String getRevertMachinePath(String namespace, int machineId) {
        return Strings.lenientFormat("%s/%s", getRevertPath(namespace), machineId);
    }

    private int nextMachineId(String namespace, int machineBit, InstanceId instanceId) throws MachineIdOverflowException {
        String counterPath = getCounterPath(namespace);
        String counterLockerPath = getCounterLockerPath(namespace);
        PromotedToLock promotedToLock = PromotedToLock.builder()
            .lockPath(counterLockerPath)
            .timeout(15, TimeUnit.SECONDS)
            .retryPolicy(retryPolicy)
            .build();

        DistributedAtomicInteger distributedAtomicInteger = new DistributedAtomicInteger(curatorFramework, counterPath, retryPolicy, promotedToLock);
        AtomicValue<Integer> atomicValue = Exceptions.invokeUnchecked(distributedAtomicInteger::increment);
        if (!atomicValue.succeeded()) {
            throw new CosIdException(Strings.lenientFormat("nextMachineId - [%s][%s->%s] concurrency conflict!", counterPath, atomicValue.preValue(), atomicValue.postValue()));
        }
        int machineId = atomicValue.postValue() - 1;

        if (machineId > maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(machineBit, instanceId);
        }
        return machineId;
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        if (log.isInfoEnabled()) {
            log.info("distribute0 - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }

        MachineState machineState = Exceptions.invokeUnchecked(() -> tryDistribute(namespace, machineBit, instanceId));
        if (log.isInfoEnabled()) {
            log.info("distribute0 - machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }

    private MachineState tryDistribute(String namespace, int machineBit, InstanceId instanceId) throws Exception {
        String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
        String revertPath = getRevertPath(namespace);
        /**
         * when {@link instanceId.stable} is true
         */
        Stat instanceStat = curatorFramework.checkExists().forPath(instancePath);
        if (Objects.nonNull(instanceStat)) {
            byte[] stateBuf = curatorFramework.getData().forPath(instancePath);
            if (stateBuf != null) {
                return MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
            }
        }
        Stat revertStat = curatorFramework.checkExists().forPath(revertPath);
        if (Objects.nonNull(revertStat) && revertStat.getNumChildren() > 0) {
            List<String> revertMachines = curatorFramework.getChildren().forPath(revertPath);
            for (String revertMachine : revertMachines) {
                String revertMachinePath = ZKPaths.makePath(revertPath, revertMachine);
                byte[] stateBuf = curatorFramework.getData().forPath(revertMachinePath);
                MachineState revertMachineState = MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
                try {
                    /**
                     * When a {@link KeeperException.NoNodeException} is thrown, it indicates that it has been obtained by other instances.
                     * Try to get the next {@link revertMachine}.
                     */
                    curatorFramework.delete().forPath(revertMachinePath);
                } catch (KeeperException.NoNodeException noNodeException) {
                    if (log.isDebugEnabled()) {
                        log.debug("distribute0 - delete revertMachinePath:[{}] failed!", revertMachinePath);
                    }
                    continue;
                }
                setMachineState(instancePath, revertMachineState);
                return revertMachineState;
            }
        }
        int machineId = nextMachineId(namespace, machineBit, instanceId);
        MachineState machineState = MachineState.of(machineId);
        setMachineState(instancePath, machineState);
        return machineState;
    }

    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revert0 - [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        MachineState revertMachineState = machineState;
        if (MachineState.NOT_FOUND.equals(revertMachineState)) {
            String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
            Stat instanceStat = Exceptions.invokeUnchecked(() -> curatorFramework.checkExists().forPath(instancePath));
            if (Objects.isNull(instanceStat)) {
                return;
            }

            byte[] stateBuf = Exceptions.invokeUnchecked(() -> curatorFramework.getData().forPath(instancePath));
            MachineState remoteMachineState = MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
            revertMachineState = MachineState.of(remoteMachineState.getMachineId(), machineState.getLastTimeStamp());
        }

        if (instanceId.isStable()) {
            revertStable(namespace, instanceId.getInstanceId(), revertMachineState);
            return;
        }
        revertTemporary(namespace, instanceId.getInstanceId(), revertMachineState);
    }

    private void revertTemporary(String namespace, String instanceId, MachineState machineState) {
        String revertMachinePath = getRevertMachinePath(namespace, machineState.getMachineId());
        String instancePath = getInstancePath(namespace, instanceId);
        Exceptions.invokeUnchecked(() -> curatorFramework.delete().forPath(instancePath));
        setMachineState(revertMachinePath, machineState);
    }

    private void revertStable(String namespace, String instanceId, MachineState machineState) {
        String instancePath = getInstancePath(namespace, instanceId);
        setMachineState(instancePath, machineState);
    }

    private void setMachineState(String path, MachineState machineState) {
        Exceptions.invokeUnchecked(() -> curatorFramework.create().orSetData().creatingParentsIfNeeded().forPath(path, machineState.toStateString().getBytes(StandardCharsets.UTF_8)));
    }

}
