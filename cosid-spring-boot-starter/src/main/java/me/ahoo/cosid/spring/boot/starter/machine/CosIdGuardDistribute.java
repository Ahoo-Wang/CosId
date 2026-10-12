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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The starter's {@link GuardDistribute}: it also reverts the machine ids it distributed when the context is destroyed
 * without being stopped, for example when a bean fails while the context starts. Otherwise those machine ids would stay
 * taken for the whole safe guard duration.
 *
 * <p>Each machine id is reverted once: on a normal shutdown {@link CosIdMachineIdLifecycle} reverts them through
 * {@link #revert(NamespacedInstanceId)}, and {@link #destroy()} has nothing left to do.
 */
@Slf4j
public class CosIdGuardDistribute extends GuardDistribute implements DisposableBean {
    private final MachineIdDistributor machineIdDistributor;
    private final MachineIdGuarder machineIdGuarder;
    private final Set<NamespacedInstanceId> reverted = ConcurrentHashMap.newKeySet();
    
    public CosIdGuardDistribute(MachineIdDistributor machineIdDistributor, MachineIdGuarder machineIdGuarder) {
        super(machineIdDistributor, machineIdGuarder);
        this.machineIdDistributor = machineIdDistributor;
        this.machineIdGuarder = machineIdGuarder;
    }
    
    /**
     * Revert the machine id of the instance, unless it was already reverted. The lease is released first, so that no
     * id is generated with the machine id once it can be handed to another instance.
     */
    public void revert(NamespacedInstanceId distributedInstance) {
        if (!reverted.add(distributedInstance)) {
            return;
        }
        try {
            machineIdGuarder.getLease(distributedInstance.getNamespace(), distributedInstance.getInstanceId()).markReleased();
            machineIdDistributor.revert(distributedInstance.getNamespace(), distributedInstance.getInstanceId());
        } catch (RuntimeException revertException) {
            log.error("Revert machine id of [{}] failed!", distributedInstance, revertException);
        }
    }
    
    @Override
    public void destroy() {
        Set<NamespacedInstanceId> distributedInstances = getDistributed();
        if (reverted.containsAll(distributedInstances)) {
            return;
        }
        if (machineIdGuarder.isRunning()) {
            machineIdGuarder.stop();
        }
        if (log.isWarnEnabled()) {
            log.warn("Reverting machine ids that were not reverted by a shutdown, probably because the context failed to start.");
        }
        for (NamespacedInstanceId distributedInstance : distributedInstances) {
            revert(distributedInstance);
        }
    }
}
