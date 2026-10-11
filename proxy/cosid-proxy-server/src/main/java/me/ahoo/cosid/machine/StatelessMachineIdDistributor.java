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

import com.google.common.base.Preconditions;

import java.time.Duration;

/**
 * Calls the remote (store) primitives of an {@link AbstractMachineIdDistributor} directly,
 * bypassing its local {@link MachineStateStorage} and {@link ClockBackwardsSynchronizer}.
 *
 * <p>The proxy server serves machine ids on behalf of its clients, so the machine state belongs to
 * each client and must never be cached in the server JVM: otherwise guards fail after a server
 * restart or behind a load balancer with several server nodes, and the server would synchronize
 * its own clock against a client's timestamp. The client keeps its own state and sends it with
 * every guard and revert.
 *
 * <p>Lives in {@code me.ahoo.cosid.machine} so it can reach the protected {@code *Remote} methods
 * without widening the core API.
 *
 * @author ahoo wang
 */
public final class StatelessMachineIdDistributor {
    private final AbstractMachineIdDistributor delegate;

    public StatelessMachineIdDistributor(AbstractMachineIdDistributor delegate) {
        this.delegate = delegate;
    }

    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {
        MachineState machineState = delegate.distributeRemote(namespace, machineBit, instanceId, safeGuardDuration);
        if (machineState.getMachineId() > MachineIdDistributor.maxMachineId(machineBit)) {
            throw new MachineIdOverflowException(MachineIdDistributor.totalMachineIds(machineBit), instanceId);
        }
        return machineState;
    }

    public void guard(String namespace, InstanceId instanceId, MachineState machineState, Duration safeGuardDuration) throws MachineIdLostException {
        checkMachineState(machineState);
        delegate.guardRemote(namespace, instanceId, machineState, safeGuardDuration);
    }

    public void revert(String namespace, InstanceId instanceId, MachineState machineState) {
        checkMachineState(machineState);
        delegate.revertRemote(namespace, instanceId, machineState);
    }

    private static void checkMachineState(MachineState machineState) {
        Preconditions.checkArgument(machineState.getMachineId() >= 0, "machineId:[%s] must be greater than or equal to 0!", machineState.getMachineId());
        Preconditions.checkArgument(machineState.getLastTimeStamp() > 0, "lastTimeStamp:[%s] must be greater than 0!", machineState.getLastTimeStamp());
    }
}
