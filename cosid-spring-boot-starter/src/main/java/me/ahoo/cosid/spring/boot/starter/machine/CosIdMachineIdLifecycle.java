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

import me.ahoo.cosid.machine.GuardianState;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import org.springframework.context.SmartLifecycle;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CosIdMachineIdLifecycle implements SmartLifecycle {
    private final MachineIdGuarder machineIdGuarder;
    private final MachineIdDistributor machineIdDistributor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder,
                                   MachineIdDistributor machineIdDistributor) {
        this.machineIdGuarder = machineIdGuarder;
        this.machineIdDistributor = machineIdDistributor;
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
            Map<NamespacedInstanceId, GuardianState> guardianStates = machineIdGuarder.getGuardianStates();
            machineIdGuarder.stop();
            guardianStates.forEach((registeredInstance, guardianState) -> {
                machineIdDistributor.revert(registeredInstance.getNamespace(), registeredInstance.getInstanceId());
            });
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
