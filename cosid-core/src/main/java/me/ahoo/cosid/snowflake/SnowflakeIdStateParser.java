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

import me.ahoo.cosid.IdGeneratorDecorator;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import javax.annotation.concurrent.ThreadSafe;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SnowflakeId State Parser.
 *
 * @author ahoo wang
 */
@ThreadSafe
public abstract class SnowflakeIdStateParser {
    
    public static final String DELIMITER = "-";
    protected final ZoneId zoneId;
    protected final long epoch;
    
    protected final int sequenceBit;
    protected final long sequenceMask;
    
    protected final int machineBit;
    protected final long machineMask;
    protected final int machineLeft;
    
    protected final int timestampBit;
    protected final long timestampMask;
    protected final int timestampLeft;
    
    public SnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit) {
        this(epoch, timestampBit, machineBit, sequenceBit, ZoneId.systemDefault());
    }
    
    public SnowflakeIdStateParser(long epoch, int timestampBit, int machineBit, int sequenceBit, ZoneId zoneId) {
        this.epoch = epoch;
        this.sequenceMask = getMask(sequenceBit);
        this.sequenceBit = sequenceBit;
        this.machineMask = getMask(machineBit);
        this.machineBit = machineBit;
        this.timestampMask = getMask(timestampBit);
        this.timestampBit = timestampBit;
        this.zoneId = zoneId;
        this.machineLeft = sequenceBit;
        this.timestampLeft = machineLeft + machineBit;
    }
    
    public ZoneId getZoneId() {
        return zoneId;
    }
    
    protected abstract DateTimeFormatter getDateTimeFormatter();
    
    protected abstract LocalDateTime getTimestamp(long diffTime);
    
    protected abstract long getDiffTime(LocalDateTime timestamp);
    
    public SnowflakeIdState parse(String friendlyId) {
        Preconditions.checkNotNull(friendlyId, "friendlyId can not be null!");
        List<String> segments = Splitter.on(DELIMITER).trimResults().omitEmptyStrings().splitToList(friendlyId);
        if (segments.size() != 3) {
            throw new IllegalArgumentException(Strings.lenientFormat("friendlyId :[%s] Illegal.", friendlyId));
        }
        String timestampStr = segments.get(0);
        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, getDateTimeFormatter());
        long machineId = Long.parseLong(segments.get(1));
        long sequence = Long.parseLong(segments.get(2));
        long diffTime = getDiffTime(timestamp);
        /**
         * machineLeft greater than 30 will cause overflow, so machineId should be long when calculating.
         */
        long id = (diffTime) << timestampLeft
            | machineId << machineLeft
            | sequence;
        return SnowflakeIdState.builder()
            .id(id)
            .machineId((int) machineId)
            .sequence(sequence)
            .timestamp(timestamp)
            .friendlyId(friendlyId)
            .build();
    }
    
    public SnowflakeIdState parse(long id) {
        int machineId = parseMachineId(id);
        long sequence = parseSequence(id);
        LocalDateTime timestamp = parseTimestamp(id);
        
        String friendlyId = new StringBuilder(timestamp.format(getDateTimeFormatter()))
            .append(DELIMITER)
            .append(machineId)
            .append(DELIMITER)
            .append(sequence)
            .toString();
        
        return SnowflakeIdState.builder()
            .id(id)
            .machineId(machineId)
            .sequence(sequence)
            .timestamp(timestamp)
            .friendlyId(friendlyId)
            .build();
    }
    
    private long getMask(long bits) {
        return ~(-1L << bits);
    }
    
    public LocalDateTime parseTimestamp(long id) {
        long diffTime = (id >> timestampLeft) & timestampMask;
        return getTimestamp(diffTime);
    }
    
    public int parseMachineId(long id) {
        return (int) ((id >> machineLeft) & machineMask);
    }
    
    public long parseSequence(long id) {
        return id & sequenceMask;
    }
    
    public static SnowflakeIdStateParser of(SnowflakeId snowflakeId) {
        return of(snowflakeId, ZoneId.systemDefault());
    }
    
    public static SnowflakeIdStateParser of(SnowflakeId snowflakeId, ZoneId zoneId) {
        SnowflakeId actual = IdGeneratorDecorator.getActual(snowflakeId);
        
        if (actual instanceof SecondSnowflakeId) {
            return SecondSnowflakeIdStateParser.of(actual, zoneId);
        }
        return MillisecondSnowflakeIdStateParser.of(actual, zoneId);
    }
}
