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

package me.ahoo.cosid.snowflake.machine.k8s;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;

/**
 * @author ahoo wang
 */
@Slf4j
public class StatefulSetMachineIdDistributor extends AbstractMachineIdDistributor {
    public static final StatefulSetMachineIdDistributor INSTANCE = new StatefulSetMachineIdDistributor(MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    public static final String HOSTNAME_KEY = "HOSTNAME";

    public StatefulSetMachineIdDistributor(MachineStateStorage machineStateStorage, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(machineStateStorage, clockBackwardsSynchronizer);
    }


    public static int resolveMachineId() {
        String hostName = System.getenv(HOSTNAME_KEY);
        Preconditions.checkNotNull(hostName, "HOSTNAME can not be null.");
        int lastSplitIdx = hostName.lastIndexOf("-");

        String idStr = hostName.substring(lastSplitIdx + 1);
        if (log.isInfoEnabled()) {
            log.info("resolveMachineId - machineId:[{}] from Env HOSTNAME:[{}]", idStr, hostName);
        }
        return Integer.parseInt(idStr);
    }

    @Override
    protected MachineState distribute0(String namespace, int machineBit, InstanceId instanceId) {
        int machineId = resolveMachineId();
        MachineState machineState = MachineState.of(machineId, NOT_FOUND_LAST_STAMP);
        if (log.isInfoEnabled()) {
            log.info("distribute0 - machineState:[{}] - instanceId:[{}] - machineBit:[{}] @ namespace:[{}].", machineState, instanceId, machineBit, namespace);
        }
        return machineState;
    }


    @Override
    protected void revert0(String namespace, InstanceId instanceId, MachineState machineState) {

    }

}
