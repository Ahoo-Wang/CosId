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

package me.ahoo.cosid.cosid;

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * CosId State.
 * <p>
 *     The {@link CosIdState} is a composite of timestamp, machineId, and sequence.
 * </p>
 */
public final class CosIdState implements Comparable<CosIdState> {
    private final long timestamp;
    private final int machineId;
    private final int sequence;
    
    public CosIdState(long timestamp, int machineId, int sequence) {
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.sequence = sequence;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getMachineId() {
        return machineId;
    }
    
    public int getSequence() {
        return sequence;
    }
    
    @Override
    public int compareTo(CosIdState o) {
        int timestampCompared = Long.compare(timestamp, o.timestamp);
        if (timestampCompared != 0) {
            return timestampCompared;
        }
        return Integer.compare(sequence, o.sequence);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CosIdState)) {
            return false;
        }
        CosIdState that = (CosIdState) o;
        return getTimestamp() == that.getTimestamp() && getMachineId() == that.getMachineId() && getSequence() == that.getSequence();
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getTimestamp(), getMachineId(), getSequence());
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("timestamp", timestamp)
            .add("machineId", machineId)
            .add("sequence", sequence)
            .toString();
    }
}
