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

import javax.annotation.concurrent.Immutable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author ahoo wang
 * Creation time: 2019/12/4 21:25
 */
@Immutable
public class SnowflakeIdState implements Comparable<SnowflakeIdState> {

    private final long id;

    private final long machineId;

    private final long sequence;

    private final LocalDateTime timestamp;
    /**
     * {@link #timestamp}-{@link #machineId}-{@link #sequence}
     */
    private final String friendlyId;

    SnowflakeIdState(long id, long machineId, long sequence, LocalDateTime timestamp, String friendlyId) {
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

    public long getMachineId() {
        return machineId;
    }

    public long getSequence() {
        return sequence;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

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
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * <p>The implementor must ensure <tt>sgn(x.compareTo(y)) ==
     * -sgn(y.compareTo(x))</tt> for all <tt>x</tt> and <tt>y</tt>.  (This
     * implies that <tt>x.compareTo(y)</tt> must throw an exception iff
     * <tt>y.compareTo(x)</tt> throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * <tt>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</tt> implies
     * <tt>x.compareTo(z)&gt;0</tt>.
     *
     * <p>Finally, the implementor must ensure that <tt>x.compareTo(y)==0</tt>
     * implies that <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for
     * all <tt>z</tt>.
     *
     * <p>It is strongly recommended, but <i>not</i> strictly required that
     * <tt>(x.compareTo(y)==0) == (x.equals(y))</tt>.  Generally speaking, any
     * class that implements the <tt>Comparable</tt> interface and violates
     * this condition should clearly indicate this fact.  The recommended
     * language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     *
     * <p>In the foregoing description, the notation
     * <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
     * <i>signum</i> function, which is defined to return one of <tt>-1</tt>,
     * <tt>0</tt>, or <tt>1</tt> according to whether the value of
     * <i>expression</i> is negative, zero or positive.
     *
     * @param other the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     */
    @Override
    public int compareTo(SnowflakeIdState other) {
        return Long.compare(this.id, other.id);
    }

    public static class SnowflakeIdStateBuilder {
        private long id;
        private long machineId;
        private long sequence;
        private LocalDateTime timestamp;
        private String friendlyId;

        SnowflakeIdStateBuilder() {
        }

        public SnowflakeIdStateBuilder id(long id) {
            this.id = id;
            return this;
        }

        public SnowflakeIdStateBuilder machineId(long machineId) {
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
            return "SnowflakeIdState.SnowflakeIdStateBuilder(id=" + this.id + ", machineId=" + this.machineId + ", sequence=" + this.sequence + ", timestamp=" + this.timestamp + ", friendlyId=" + this.friendlyId + ")";
        }
    }
}
