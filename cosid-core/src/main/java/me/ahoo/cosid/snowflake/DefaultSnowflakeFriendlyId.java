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

package me.ahoo.cosid.snowflake;

import java.time.ZoneId;

/**
 * @author ahoo wang
 */
public class DefaultSnowflakeFriendlyId implements SnowflakeFriendlyId {
    private final SnowflakeId delegate;
    private final SnowflakeIdStateParser snowflakeIdStateParser;

    public DefaultSnowflakeFriendlyId(SnowflakeId delegate) {
        this(delegate, ZoneId.systemDefault());
    }

    public DefaultSnowflakeFriendlyId(SnowflakeId delegate, ZoneId zoneId) {
        this.delegate = delegate;
        this.snowflakeIdStateParser = SnowflakeIdStateParser.of(delegate, zoneId);
    }

    public SnowflakeId getDelegate() {
        return delegate;
    }
    @Override
    public SnowflakeIdStateParser getParser() {
        return snowflakeIdStateParser;
    }

    @Override
    public long generate() {
        return delegate.generate();
    }

    @Override
    public SnowflakeIdState friendlyId(long id) {
        return snowflakeIdStateParser.parse(id);
    }

    @Override
    public SnowflakeIdState ofFriendlyId(String friendlyId) {
        return snowflakeIdStateParser.parse(friendlyId);
    }

    @Override
    public long getEpoch() {
        return delegate.getEpoch();
    }

    @Override
    public int getTimestampBit() {
        return delegate.getTimestampBit();
    }

    @Override
    public int getMachineBit() {
        return delegate.getMachineBit();
    }

    @Override
    public int getSequenceBit() {
        return delegate.getSequenceBit();
    }

    @Override
    public long getMaxTimestamp() {
        return delegate.getMaxTimestamp();
    }

    @Override
    public long getMaxMachine() {
        return delegate.getMaxMachine();
    }

    @Override
    public long getMaxSequence() {
        return delegate.getMaxSequence();
    }

    @Override
    public long getLastTimestamp() {
        return delegate.getLastTimestamp();
    }

    @Override
    public long getMachineId() {
        return delegate.getMachineId();
    }
}
