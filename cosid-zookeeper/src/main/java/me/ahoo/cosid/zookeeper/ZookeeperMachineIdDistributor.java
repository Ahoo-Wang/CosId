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
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.AbstractMachineIdDistributor;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdLostException;
import me.ahoo.cosid.machine.MachineIdOverflowException;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Zookeeper MachineIdDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class ZookeeperMachineIdDistributor extends AbstractMachineIdDistributor {
    
    /**
     * /cosid/{namespace}/__itc_idx/{instanceId} .
     * data:{@link MachineState#toStateString()}
     */
    private static final String INSTANCE_IDX_PATH = "__itc_idx";
    /**
     * /cosid/{namespace}/__revert/{machineId} .
     * data:lastStamp
     */
    private static final String REVERT_PATH = "__revert";
    
    private final CuratorFramework curatorFramework;
    private final RetryPolicy retryPolicy;
    
    public ZookeeperMachineIdDistributor(CuratorFramework curatorFramework,
                                         RetryPolicy retryPolicy,
                                         MachineStateStorage machineStateStorage,
                                         ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
        this.curatorFramework = curatorFramework;
        this.retryPolicy = retryPolicy;
    }
    
    /**
     * /cosid/{namespace}/__counter .
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
        
        if (machineId > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        return machineId;
    }
    
    @Override
    protected MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) {
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", instanceId, machineBit, namespace);
        }
        
        MachineState machineState = Exceptions.invokeUnchecked(() -> tryDistribute(namespace, machineBit, instanceId, safeGuardDuration));
        if (log.isInfoEnabled()) {
            log.info("Distribute Remote machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }
    
    private MachineState tryDistribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws Exception {
        String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
        MachineState selfState = distributeBySelf(instancePath);
        if (selfState != null) {
            return selfState;
        }
        MachineState revertState = distributeByRevert(namespace, instancePath);
        if (revertState != null) {
            return revertState;
        }
        
        try {
            int machineId = nextMachineId(namespace, machineBit, instanceId);
            MachineState machineState = MachineState.of(machineId);
            setMachineState(instancePath, machineState);
            return machineState;
        } catch (MachineIdOverflowException overflowException) {
            MachineState recyclableState = distributeByRecyclable(namespace, instancePath, instanceId, safeGuardDuration);
            if (recyclableState != null) {
                return recyclableState;
            }
            throw overflowException;
        }
    }
    
    private MachineState distributeBySelf(String instancePath) throws Exception {
        /**
         * when {@link instanceId.stable} is true .
         */
        try {
            byte[] stateBuf = curatorFramework.getData().forPath(instancePath);
            if (stateBuf != null) {
                return MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
            }
        } catch (KeeperException.NoNodeException noNodeException) {
            return null;
        }
        return null;
    }
    
    private MachineState distributeByRevert(String namespace, String instancePath) throws Exception {
        String revertPath = getRevertPath(namespace);
        Stat revertStat = curatorFramework.checkExists().forPath(revertPath);
        if (Objects.nonNull(revertStat) && revertStat.getNumChildren() > 0) {
            List<String> revertMachines = curatorFramework.getChildren().forPath(revertPath);
            for (String revertMachine : revertMachines) {
                String revertMachinePath = ZKPaths.makePath(revertPath, revertMachine);
                Stat stat = new Stat();
                MachineState revertMachineState;
                try {
                    byte[] stateBuf = curatorFramework.getData().storingStatIn(stat).forPath(revertMachinePath);
                    revertMachineState = MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
                } catch (KeeperException.NoNodeException noNodeException) {
                    continue;
                }
                /*
                 * Claim the reverted machine id atomically: the revert node is removed only if nobody changed it since it was read,
                 * and the instance node is created in the same transaction, so a crash can never leak the machine id.
                 */
                if (!tryMove(revertMachinePath, stat.getVersion(), instancePath, revertMachineState)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Try Distribute - claim revertMachinePath:[{}] failed!", revertMachinePath);
                    }
                    continue;
                }
                return revertMachineState;
            }
        }
        return null;
    }
    
    protected MachineState distributeByRecyclable(String namespace, String instancePath, InstanceId instanceId, Duration safeGuardDuration) throws Exception {
        String instanceIdxPath = getInstanceIdxPath(namespace);
        List<String> instanceMachines = curatorFramework.getChildren().forPath(instanceIdxPath);
        for (String eachInstance : instanceMachines) {
            String eachInstancePath = ZKPaths.makePath(instanceIdxPath, eachInstance);
            if (eachInstancePath.equals(instancePath)) {
                continue;
            }
            Stat stat = new Stat();
            MachineState instanceMachineState;
            try {
                byte[] stateBuf = curatorFramework.getData().storingStatIn(stat).forPath(eachInstancePath);
                instanceMachineState = MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
            } catch (KeeperException.NoNodeException noNodeException) {
                if (log.isDebugEnabled()) {
                    log.debug("Try Distribute - read recyclable instancePath:[{}] failed!", eachInstancePath);
                }
                continue;
            }
            long safeGuardAt = MachineIdDistributor.getSafeGuardAt(safeGuardDuration, instanceId.isStable());
            
            if (instanceMachineState.getLastTimeStamp() > safeGuardAt) {
                continue;
            }
            MachineState machineState = MachineState.of(instanceMachineState.getMachineId(), Math.max(instanceMachineState.getLastTimeStamp(), System.currentTimeMillis()));
            /*
             * The versioned delete fails if the owner guarded (setData) after the read above, so a live machine id is never taken over.
             */
            if (!tryMove(eachInstancePath, stat.getVersion(), instancePath, machineState)) {
                if (log.isDebugEnabled()) {
                    log.debug("Try Distribute - claim recyclable instancePath:[{}] failed!", eachInstancePath);
                }
                continue;
            }
            return machineState;
        }
        return null;
    }
    
    @Override
    protected void revertRemote(String namespace, InstanceId instanceId, MachineState machineState) {
        if (log.isInfoEnabled()) {
            log.info("Revert Remote [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
        Stat stat = new Stat();
        MachineState remoteMachineState;
        try {
            byte[] stateBuf = curatorFramework.getData().storingStatIn(stat).forPath(instancePath);
            remoteMachineState = MachineState.of(new String(stateBuf, StandardCharsets.UTF_8));
        } catch (KeeperException.NoNodeException noNodeException) {
            if (log.isWarnEnabled()) {
                log.warn("Revert Remote [{}] instanceId:[{}] @ namespace:[{}] - instance node not found, machine id may have been recycled.", machineState, instanceId, namespace);
            }
            return;
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
        if (!MachineState.NOT_FOUND.equals(machineState) && remoteMachineState.getMachineId() != machineState.getMachineId()) {
            if (log.isWarnEnabled()) {
                log.warn("Revert Remote [{}] instanceId:[{}] @ namespace:[{}] - remote machine id [{}] does not match, skip revert.",
                    machineState, instanceId, namespace, remoteMachineState.getMachineId());
            }
            return;
        }
        MachineState revertMachineState = MachineState.of(remoteMachineState.getMachineId(), machineState.getLastTimeStamp());
        
        if (instanceId.isStable()) {
            revertStable(instancePath, stat.getVersion(), revertMachineState);
            return;
        }
        revertTemporary(namespace, instancePath, stat.getVersion(), revertMachineState);
    }
    
    @Override
    protected void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) {
        if (log.isDebugEnabled()) {
            log.debug("Guard Remote [{}] instanceId:[{}] @ namespace:[{}].", machineState, instanceId, namespace);
        }
        String instancePath = getInstancePath(namespace, instanceId.getInstanceId());
        try {
            curatorFramework.setData().forPath(instancePath, machineState.toStateString().getBytes(StandardCharsets.UTF_8));
        } catch (KeeperException.NoNodeException noNodeException) {
            throw new MachineIdLostException(namespace, instanceId, machineState);
        } catch (RuntimeException | Error runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
    }
    
    private void revertTemporary(String namespace, String instancePath, int instanceVersion, MachineState machineState) {
        String revertMachinePath = getRevertMachinePath(namespace, machineState.getMachineId());
        if (!tryMove(instancePath, instanceVersion, revertMachinePath, machineState)) {
            if (log.isWarnEnabled()) {
                log.warn("Revert Remote [{}] - instancePath:[{}] changed concurrently, skip revert.", machineState, instancePath);
            }
        }
    }
    
    private void revertStable(String instancePath, int instanceVersion, MachineState machineState) {
        try {
            curatorFramework.setData().withVersion(instanceVersion).forPath(instancePath, toBytes(machineState));
        } catch (KeeperException.NoNodeException | KeeperException.BadVersionException conflictException) {
            if (log.isWarnEnabled()) {
                log.warn("Revert Remote [{}] - instancePath:[{}] changed concurrently, skip revert.", machineState, instancePath);
            }
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
    }
    
    /**
     * Atomically delete {@code fromPath} (only if its version is still {@code fromVersion}) and create {@code toPath}.
     *
     * @return {@code false} if another client changed or removed {@code fromPath}, or created {@code toPath}, concurrently.
     */
    private boolean tryMove(String fromPath, int fromVersion, String toPath, MachineState machineState) {
        ensureParent(toPath);
        try {
            curatorFramework.transaction().forOperations(
                curatorFramework.transactionOp().delete().withVersion(fromVersion).forPath(fromPath),
                curatorFramework.transactionOp().create().forPath(toPath, toBytes(machineState))
            );
            return true;
        } catch (KeeperException.NoNodeException | KeeperException.BadVersionException | KeeperException.NodeExistsException conflictException) {
            return false;
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
    }
    
    private void ensureParent(String path) {
        String parentPath = ZKPaths.getPathAndNode(path).getPath();
        try {
            curatorFramework.create().creatingParentsIfNeeded().forPath(parentPath, new byte[0]);
        } catch (KeeperException.NodeExistsException ignored) {
            // already created.
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
    }
    
    private static byte[] toBytes(MachineState machineState) {
        return machineState.toStateString().getBytes(StandardCharsets.UTF_8);
    }
    
    private void setMachineState(String path, MachineState machineState) {
        Exceptions.invokeUnchecked(() -> curatorFramework.create().orSetData().creatingParentsIfNeeded().forPath(path, toBytes(machineState)));
    }
    
}
