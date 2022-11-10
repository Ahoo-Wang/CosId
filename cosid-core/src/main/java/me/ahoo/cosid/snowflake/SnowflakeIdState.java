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

package me.ahoo.cosid.snowflake;

import java.time.LocalDateTime;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * SnowflakeId State.
 *
 * @author ahoo wang
 */
@Immutable
public class SnowflakeIdState implements Comparable<SnowflakeIdState> {
    
    private final long id;
    
    private final int machineId;
    
    private final long sequence;
    
    private final LocalDateTime timestamp;
    /**
     * {@link #timestamp}-{@link #machineId}-{@link #sequence} .
     */
    private final String friendlyId;
    
    SnowflakeIdState(long id, int machineId, long sequence, LocalDateTime timestamp, String friendlyId) {
        this.id = id;
        this.machineId = machineId;
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.friendlyId = friendlyId;
    }
    
    public static SnowflakeIdStateBuilder builder() {
        return new SnowflakeIdStateBuilder();
    }
    
    public long getId() {
        return id;
    }
    
    public int getMachineId() {
        return machineId;
    }
    
    public long getSequence() {
        return sequence;
    }
    
    @Nonnull
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    @Nonnull
    public String getFriendlyId() {
        return friendlyId;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (Objects.isNull(other) || !getClass().isInstance(other)) {
            return false;
        }
        SnowflakeIdState that = (SnowflakeIdState) other;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
    
    @Override
    public String toString() {
        return friendlyId;
    }
    
    @Override
    public int compareTo(SnowflakeIdState other) {
        return Long.compare(this.id, other.id);
    }
    
    public static class SnowflakeIdStateBuilder {
        private long id;
        private int machineId;
        private long sequence;
        private LocalDateTime timestamp;
        private String friendlyId;
        
        SnowflakeIdStateBuilder() {
        }
        
        public SnowflakeIdStateBuilder id(long id) {
            this.id = id;
            return this;
        }
        
        public SnowflakeIdStateBuilder machineId(int machineId) {
            this.machineId = machineId;
            return this;
        }
        
        public SnowflakeIdStateBuilder sequence(long sequence) {
            this.sequence = sequence;
            return this;
        }
        
        public SnowflakeIdStateBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public SnowflakeIdStateBuilder friendlyId(String friendlyId) {
            this.friendlyId = friendlyId;
            return this;
        }
        
        public SnowflakeIdState build() {
            return new SnowflakeIdState(id, machineId, sequence, timestamp, friendlyId);
        }
        
        public String toString() {
            return "SnowflakeIdState.SnowflakeIdStateBuilder(id="
                + this.id
                + ", machineId="
                + this.machineId
                + ", sequence="
                + this.sequence
                + ", timestamp="
                + this.timestamp
                + ", friendlyId="
                + this.friendlyId + ")";
        }
    }
}
