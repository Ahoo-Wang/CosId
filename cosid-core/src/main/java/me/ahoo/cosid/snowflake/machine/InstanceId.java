/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 * @see MachineId
 */
public class InstanceId {
    public static final InstanceId NONE = new InstanceId("none", false);

    private final String instanceId;
    private final boolean stable;

    public InstanceId(String instanceId, boolean stable) {
        this.instanceId = instanceId;
        this.stable = stable;
    }

    /**
     * 稳定的的实例拥有稳定的机器号
     *
     * @return
     */
    public boolean isStable() {
        return stable;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return "InstanceId{" +
                "instanceId='" + instanceId + '\'' +
                ", stable=" + stable +
                '}';
    }

    public static InstanceId of(String host, int port, boolean stable) {
        String instanceIdStr = String.format("%s:%s", host, port);
        return of(instanceIdStr, stable);
    }

    public static InstanceId of(String instanceId, boolean stable) {
        return new InstanceId(instanceId, stable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstanceId)) return false;
        InstanceId that = (InstanceId) o;
        return stable == that.stable && Objects.equal(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instanceId, stable);
    }
}
