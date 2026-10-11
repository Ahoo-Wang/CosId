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
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the machine id guarder and reverts every distributed machine id on shutdown.
 *
 * <p>Runs in {@link #PHASE}: it stops after the web server has finished its graceful shutdown, so requests that are still
 * draining never generate ids with a machine id that was already reverted, and before connection factories that stop in
 * phase {@code 0} (such as Lettuce), so the revert can still reach the backend.
 */
@Slf4j
public class CosIdMachineIdLifecycle implements SmartLifecycle {
    /**
     * Below Spring Boot's web server lifecycles ({@code DEFAULT_PHASE - 1024} and {@code DEFAULT_PHASE - 2048}).
     */
    public static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;
    private final MachineIdGuarder machineIdGuarder;
    private final MachineIdDistributor machineIdDistributor;
    @Nullable
    private final GuardDistribute guardDistribute;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder,
                                   MachineIdDistributor machineIdDistributor,
                                   @Nullable GuardDistribute guardDistribute) {
        this.machineIdGuarder = machineIdGuarder;
        this.machineIdDistributor = machineIdDistributor;
        this.guardDistribute = guardDistribute;
    }

    /**
     * Reverts only the instances registered in the guarder.
     *
     * @deprecated use {@link #CosIdMachineIdLifecycle(MachineIdGuarder, MachineIdDistributor, GuardDistribute)}, which also
     *     reverts machine ids when the guarder is disabled.
     */
    @Deprecated
    public CosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder,
                                   MachineIdDistributor machineIdDistributor) {
        this(machineIdGuarder, machineIdDistributor, null);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            machineIdGuarder.start();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            Set<NamespacedInstanceId> distributedInstances = new LinkedHashSet<>(machineIdGuarder.getGuardianStates().keySet());
            if (guardDistribute != null) {
                distributedInstances.addAll(guardDistribute.getDistributed());
            }
            machineIdGuarder.stop();
            for (NamespacedInstanceId distributedInstance : distributedInstances) {
                revert(distributedInstance);
            }
        }
    }

    private void revert(NamespacedInstanceId distributedInstance) {
        try {
            machineIdDistributor.revert(distributedInstance.getNamespace(), distributedInstance.getInstanceId());
        } catch (RuntimeException revertException) {
            log.error("Revert machine id of [{}] failed!", distributedInstance, revertException);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
