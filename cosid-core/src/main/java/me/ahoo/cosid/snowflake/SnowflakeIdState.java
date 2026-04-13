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

import com.google.errorprone.annotations.Immutable;
import org.jspecify.annotations.NonNull;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable state object representing a parsed Snowflake ID.
 *
 * <p>This class holds the decomposed components of a Snowflake ID:
 * the raw ID, machine ID, sequence number, timestamp, and a human-readable
 * friendly format ({@code timestamp-machineId-sequence}).
 *
 * @author ahoo wang
 */
@Immutable
public class SnowflakeIdState implements Comparable<SnowflakeIdState> {

    /**
     * The raw 64-bit snowflake ID value.
     */
    private final long id;

    /**
     * The machine ID portion of the ID.
     */
    private final int machineId;

    /**
     * The sequence number portion of the ID.
     */
    private final long sequence;

    /**
     * The timestamp when this ID was generated.
     */
    private final LocalDateTime timestamp;

    /**
     * Human-readable representation in format {@code timestamp-machineId-sequence}.
     */
    private final String friendlyId;

    SnowflakeIdState(long id, int machineId, long sequence, LocalDateTime timestamp, String friendlyId) {
        this.id = id;
        this.machineId = machineId;
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.friendlyId = friendlyId;
    }

    /**
     * Creates a new builder for SnowflakeIdState.
     *
     * @return a new builder instance
     */
    public static SnowflakeIdStateBuilder builder() {
        return new SnowflakeIdStateBuilder();
    }

    /**
     * Gets the raw snowflake ID value.
     *
     * @return the raw 64-bit ID
     */
    public long getId() {
        return id;
    }

    /**
     * Gets the machine ID component.
     *
     * @return the machine ID
     */
    public int getMachineId() {
        return machineId;
    }

    /**
     * Gets the sequence number component.
     *
     * @return the sequence number
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * Gets the timestamp when this ID was generated.
     *
     * @return the timestamp as LocalDateTime
     */
    @NonNull
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the human-readable form of this ID.
     *
     * @return friendly ID in format {@code timestamp-machineId-sequence}
     */
    @NonNull
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

    /**
     * Compares this state to another by raw ID value.
     *
     * @param other the other SnowflakeIdState to compare
     * @return comparison result
     */
    @Override
    public int compareTo(SnowflakeIdState other) {
        return Long.compare(this.id, other.id);
    }

    /**
     * Builder for SnowflakeIdState.
     */
    public static class SnowflakeIdStateBuilder {
        private long id;
        private int machineId;
        private long sequence;
        private LocalDateTime timestamp;
        private String friendlyId;

        SnowflakeIdStateBuilder() {
        }

        /**
         * Sets the raw snowflake ID value.
         *
         * @param id the raw 64-bit ID
         * @return this builder
         */
        public SnowflakeIdStateBuilder id(long id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the machine ID component.
         *
         * @param machineId the machine ID
         * @return this builder
         */
        public SnowflakeIdStateBuilder machineId(int machineId) {
            this.machineId = machineId;
            return this;
        }

        /**
         * Sets the sequence number component.
         *
         * @param sequence the sequence number
         * @return this builder
         */
        public SnowflakeIdStateBuilder sequence(long sequence) {
            this.sequence = sequence;
            return this;
        }

        /**
         * Sets the timestamp component.
         *
         * @param timestamp the timestamp
         * @return this builder
         */
        public SnowflakeIdStateBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the friendly ID string.
         *
         * @param friendlyId the friendly ID string
         * @return this builder
         */
        public SnowflakeIdStateBuilder friendlyId(String friendlyId) {
            this.friendlyId = friendlyId;
            return this;
        }

        /**
         * Builds the SnowflakeIdState instance.
         *
         * @return the built instance
         */
        public SnowflakeIdState build() {
            return new SnowflakeIdState(id, machineId, sequence, timestamp, friendlyId);
        }

        @Override
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
