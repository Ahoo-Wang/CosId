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

package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * 逻辑概念的机器号，并不一定跟物理机/虚拟机一一对应，运行进程的唯一性编号(不同业务领域/服务使用 namespace 隔离).
 *
 * @author ahoo wang
 * @see InstanceId
 */
@Immutable
public class MachineId {
    private final long machineId;

    public MachineId(long machineId) {
        this.machineId = machineId;
    }

    public long getMachineId() {
        return machineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MachineId)) {
            return false;
        }
        MachineId machineId1 = (MachineId) o;
        return getMachineId() == machineId1.getMachineId();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getMachineId());
    }
}
