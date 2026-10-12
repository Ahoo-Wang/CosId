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

package me.ahoo.cosid.machine;

import static me.ahoo.cosid.machine.ClockBackwardsSynchronizer.getBackwardsTimeStamp;

import me.ahoo.cosid.internal.Checks;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.time.Duration;

/**
 * Abstract MachineIdDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMachineIdDistributor implements MachineIdDistributor {
    public static final int NOT_FOUND_LAST_STAMP = -1;
    private final MachineStateStorage machineStateStorage;
    private final Object localStateLock = new Object();
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;

    public AbstractMachineIdDistributor(MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        this.machineStateStorage = machineStateStorage;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
    }

    /**
     * 1. {@link #distributeRemote} — the remote store is the only authority for which instance owns a machine id.
     * 2. use the local {@link MachineStateStorage} only as a clock watermark when it holds the same machine id.
     * 3. wait for clock backwards, releasing the machine id if the clock is too far behind.
     * 4. set {@link MachineState} to {@link MachineStateStorage}
     *
     * <p>A local state must never be trusted on its own: after a crash the remote lease may have expired and the machine id
     * may already belong to another instance.
     */
    @Override
    public @NonNull MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {

        Checks.checkArgument(!Checks.isNullOrEmpty(namespace), "namespace can not be empty!");
        Checks.checkArgument(machineBit > 0, "machineBit:[%s] must be greater than 0!", machineBit);
        Checks.checkNotNull(instanceId, "instanceId can not be null!");

        MachineState localState = machineStateStorage.get(namespace, instanceId);
        MachineState remoteState = distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
        try {
            ensureMachineId(machineBit, instanceId, remoteState);
        } catch (RuntimeException outOfRange) {
            releaseAfterFailedDistribute(namespace, instanceId, remoteState, outOfRange);
            throw outOfRange;
        }

        long lastTimeStamp = remoteState.getLastTimeStamp();
        if (!MachineState.NOT_FOUND.equals(localState)) {
            if (localState.getMachineId() == remoteState.getMachineId()) {
                lastTimeStamp = Math.max(lastTimeStamp, localState.getLastTimeStamp());
            } else if (log.isWarnEnabled()) {
                log.warn("Distribute [{}] @ namespace:[{}] - local machine id [{}] is stale, the remote store distributed [{}].",
                    instanceId, namespace, localState.getMachineId(), remoteState.getMachineId());
            }
        }

        MachineState machineState = remoteState;
        if (ClockBackwardsSynchronizer.getBackwardsTimeStamp(lastTimeStamp) > 0) {
            try {
                clockBackwardsSynchronizer.syncUninterruptibly(lastTimeStamp);
            } catch (RuntimeException syncException) {
                releaseAfterFailedDistribute(namespace, instanceId, remoteState, syncException);
                throw syncException;
            }
            machineState = MachineState.of(remoteState.getMachineId(), System.currentTimeMillis());
        }

        try {
            synchronized (localStateLock) {
                machineStateStorage.set(namespace, machineState.getMachineId(), instanceId);
            }
        } catch (RuntimeException localStateException) {
            // The machine id is taken in the store: give it back, or it stays blocked for the safe guard duration.
            releaseAfterFailedDistribute(namespace, instanceId, machineState, localStateException);
            throw localStateException;
        }
        return machineState;
    }

    private void releaseAfterFailedDistribute(String namespace, InstanceId instanceId, MachineState machineState, RuntimeException cause) {
        try {
            revertRemote(namespace, instanceId, machineState);
        } catch (RuntimeException revertException) {
            cause.addSuppressed(revertException);
        }
    }

    protected abstract MachineState distributeRemote(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration);

    private void ensureMachineId(int machineBit, InstanceId instanceId, MachineState machineState) {
        if (machineState.getMachineId() > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
    }

    /**
     * 1. get from {@link MachineStateStorage}
     * 2. when not found: {@link #distributeRemote} , no need to revert
     * 3. revert
     *
     * @param namespace namespace
     * @param instanceId instanceId
     */
    @Override
    public void revert(String namespace, InstanceId instanceId) {
        MachineState lastLocalState = resetStorage(namespace, instanceId);

        try {
            revertRemote(namespace, instanceId, lastLocalState);
        } finally {
            // Also when revertRemote fails or times out (it may still have been applied remotely):
            // the next start must then ask the remote instead of trusting a stale local state.
            synchronized (localStateLock) {
                machineStateStorage.remove(namespace, instanceId);
            }
        }
    }

    protected abstract void revertRemote(String namespace, InstanceId instanceId, MachineState machineState);

    @Override
    public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) throws NotFoundMachineStateException, MachineIdLostException {
        MachineState lastLocalState = resetStorage(namespace, instanceId);
        guardRemote(namespace, instanceId, lastLocalState, safeGuardDuration);
    }


    @Override
    public void refreshLocalState(String namespace, InstanceId instanceId) {
        // Atomic with the other local state writes: a refresh must never bring back the state of a reverted or replaced machine id.
        synchronized (localStateLock) {
            MachineState lastLocalState = machineStateStorage.get(namespace, instanceId);
            if (MachineState.NOT_FOUND.equals(lastLocalState)) {
                return;
            }
            if (getBackwardsTimeStamp(lastLocalState.getLastTimeStamp()) < 0) {
                machineStateStorage.set(namespace, lastLocalState.getMachineId(), instanceId);
            }
        }
    }

    private MachineState resetStorage(String namespace, InstanceId instanceId) {
        Checks.checkArgument(!Checks.isNullOrEmpty(namespace), "namespace can not be empty!");
        Checks.checkNotNull(instanceId, "instanceId can not be null!");

        synchronized (localStateLock) {
            MachineState lastLocalState = machineStateStorage.get(namespace, instanceId);
            if (MachineState.NOT_FOUND.equals(lastLocalState)) {
                throw new NotFoundMachineStateException(namespace, instanceId);
            }
            if (getBackwardsTimeStamp(lastLocalState.getLastTimeStamp()) < 0) {
                lastLocalState = MachineState.of(lastLocalState.getMachineId(), System.currentTimeMillis());
                machineStateStorage.set(namespace, lastLocalState.getMachineId(), instanceId);
            }
            return lastLocalState;
        }
    }

    protected abstract void guardRemote(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration);

}
