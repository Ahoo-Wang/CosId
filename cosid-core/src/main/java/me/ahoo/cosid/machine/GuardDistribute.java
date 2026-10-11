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

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GuardDistribute implements MachineIdDistribute {
    private final MachineIdDistributor machineIdDistributor;
    private final MachineIdGuarder machineIdGuarder;
    private final Set<NamespacedInstanceId> distributed = ConcurrentHashMap.newKeySet();

    public GuardDistribute(MachineIdDistributor machineIdDistributor, MachineIdGuarder machineIdGuarder) {
        this.machineIdDistributor = machineIdDistributor;
        this.machineIdGuarder = machineIdGuarder;
    }

    @NonNull
    @Override
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {
        MachineState machineState = machineIdDistributor.distribute(namespace, machineBit, instanceId, safeGuardDuration);
        distributed.add(new NamespacedInstanceId(namespace, instanceId));
        machineIdGuarder.register(namespace, instanceId);
        return machineState;
    }

    /**
     * Every machine id distributed through this instance, whether or not a guarder is running,
     * so that they can all be reverted on shutdown.
     *
     * @return a snapshot of the distributed instances
     */
    @NonNull
    public Set<NamespacedInstanceId> getDistributed() {
        return Set.copyOf(distributed);
    }
}
