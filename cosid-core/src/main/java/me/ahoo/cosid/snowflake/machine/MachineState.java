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

package me.ahoo.cosid.snowflake.machine;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import javax.annotation.concurrent.Immutable;
import java.util.List;

/**
 * @author ahoo wang
 */
@Immutable
public class MachineState {
    public static final MachineState NOT_FOUND = of(-1, -1);
    public static final String STATE_DELIMITER = "|";
    private final int machineId;
    private final long lastTimeStamp;

    public MachineState(int machineId, long lastTimeStamp) {
        this.machineId = machineId;
        this.lastTimeStamp = lastTimeStamp;
    }

    public int getMachineId() {
        return machineId;
    }

    public long getLastTimeStamp() {
        return lastTimeStamp;
    }

    @Override
    public String toString() {
        return "MachineState{" +
                "machineId=" + machineId +
                ", lastTimeStamp=" + lastTimeStamp +
                '}';
    }

    public String toStateString() {
        return Strings.lenientFormat("%s%s%s", machineId, STATE_DELIMITER, System.currentTimeMillis());
    }

    public static MachineState of(int machineId, long lastStamp) {
        return new MachineState(machineId, lastStamp);
    }

    public static MachineState of(String stateString) {
        List<String> stateSplits = Splitter.on(STATE_DELIMITER).omitEmptyStrings().splitToList(stateString);
        if (stateSplits.size() != 2) {
            throw new IllegalArgumentException(Strings.lenientFormat("Machine status data:[{%s}] format error.", stateString));
        }
        int machineId = Integer.parseInt(stateSplits.get(0));
        long lastStamp = Long.parseLong(stateSplits.get(1));
        return MachineState.of(machineId, lastStamp);
    }
}
