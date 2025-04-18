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

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Strings;
import jakarta.annotation.Nullable;

/**
 * MachineId Lost Exception .
 *
 * @author ahoo wang
 */
public class MachineIdLostException extends CosIdException {
    private final String namespace;
    private final InstanceId instanceId;
    private final MachineState machineState;
    
    public MachineIdLostException(String namespace, InstanceId instanceId, @Nullable MachineState machineState) {
        super(Strings.lenientFormat("The machine id[%s] bound to the instance[%s]@[%s] has been lost!.", machineState, instanceId, namespace));
        this.namespace = namespace;
        this.instanceId = instanceId;
        this.machineState = machineState;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public InstanceId getInstanceId() {
        return instanceId;
    }
    
    public MachineState getMachineState() {
        return machineState;
    }
}
