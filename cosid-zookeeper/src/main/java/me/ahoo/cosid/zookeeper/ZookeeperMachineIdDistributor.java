/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicInteger;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

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

    public ZookeeperMachineIdDistributor(CuratorFramework curatorFramework, MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.curatorFramework = curatorFramework;
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
        DistributedAtomicInteger distributedAtomicInteger = new DistributedAtomicInteger(curatorFramework, getCounterPath(namespace), new RetryNTimes(3, 10));
        int machineId;
        try {
            machineId = distributedAtomicInteger.increment().postValue() - 1;
        } catch (Exception e) {
            throw new CosIdException(e.getMessage(), e);
        }

        if (machineId > maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(machineBit, instanceId);
        }
        return machineId;
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
        String revertPath = getRevertPath(namespace);
        try {
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
        } catch (MachineIdOverflowException overflowException) {
            throw overflowException;
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
    }

    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("revert0 - instanceId:[{}] @ namespace:[{}].", instanceId, namespace);
        }

        if (MachineState.NOT_FOUND.equals(machineState)) {
            try {
                String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
                Stat instanceStat = curatorFramework.checkExists().forPath(instancePath);
                if (Objects.isNull(instanceStat)) {
                    return;
                }
                byte[] stateBuf = curatorFramework.getData().forPath(instancePath);
                MachineState remoteMachineState = MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
                machineState = MachineState.of(remoteMachineState.getMachineId(), machineState.getLastTimeStamp());
            } catch (Exception exception) {
                throw new CosIdException(exception.getMessage(), exception);
            }
        }

        if (instanceId.isStable()) {
            revertStable(namespace, instanceId.getInstanceId(), machineState);
            return;
        }
        revertTemporary(namespace, instanceId.getInstanceId(), machineState);
    }

    private void revertTemporary(String namespace, String instanceId, MachineState machineState) {
        String revertMachinePath = getRevertMachinePath(namespace, machineState.getMachineId());
        String instancePath = getInstancePath(namespace, instanceId);
        try {
            curatorFramework.delete().forPath(instancePath);
            setMachineState(revertMachinePath, machineState);
        } catch (Exception e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }

    private void revertStable(String namespace, String instanceId, MachineState machineState) {
        String instancePath = getInstancePath(namespace, instanceId);
        try {
            setMachineState(instancePath, machineState);
        } catch (Exception e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }

    private void setMachineState(String path, MachineState machineState) throws Exception {
        curatorFramework.create().orSetData().creatingParentsIfNeeded().forPath(path, machineState.toStateString().getBytes(StandardCharsets.UTF_8));
    }

}
