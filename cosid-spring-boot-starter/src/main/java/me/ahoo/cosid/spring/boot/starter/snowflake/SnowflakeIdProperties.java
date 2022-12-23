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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import javax.annotation.Nonnull;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * SnowflakeId Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = SnowflakeIdProperties.PREFIX)
public class SnowflakeIdProperties {
    public static final String PREFIX = CosId.COSID_PREFIX + "snowflake";
    
    private boolean enabled = false;
    private String zoneId = ZoneId.systemDefault().getId();
    private long epoch = CosId.COSID_EPOCH;
    private IdDefinition share;
    private Map<String, IdDefinition> provider;
    
    public SnowflakeIdProperties() {
        share = new IdDefinition();
        provider = new HashMap<>();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getZoneId() {
        return zoneId;
    }
    
    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }
    
    public long getEpoch() {
        return epoch;
    }
    
    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }
    
    public IdDefinition getShare() {
        return share;
    }
    
    public void setShare(IdDefinition share) {
        this.share = share;
    }
    
    @Nonnull
    public Map<String, IdDefinition> getProvider() {
        return provider;
    }
    
    public void setProvider(Map<String, IdDefinition> provider) {
        this.provider = provider;
    }
    
    public static class IdDefinition {
        private boolean clockSync = true;
        private boolean friendly = true;
        private TimestampUnit timestampUnit = TimestampUnit.MILLISECOND;
        private long epoch;
        private int timestampBit = MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        private int sequenceBit = MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT;
        private long sequenceResetThreshold = MillisecondSnowflakeId.DEFAULT_SEQUENCE_RESET_THRESHOLD;
        @NestedConfigurationProperty
        private IdConverterDefinition converter = new IdConverterDefinition();
        
        public boolean isClockSync() {
            return clockSync;
        }
        
        public void setClockSync(boolean clockSync) {
            this.clockSync = clockSync;
        }
        
        public boolean isFriendly() {
            return friendly;
        }
        
        public void setFriendly(boolean friendly) {
            this.friendly = friendly;
        }
        
        public TimestampUnit getTimestampUnit() {
            return timestampUnit;
        }
        
        public void setTimestampUnit(TimestampUnit timestampUnit) {
            this.timestampUnit = timestampUnit;
        }
        
        public long getEpoch() {
            return epoch;
        }
        
        public void setEpoch(long epoch) {
            this.epoch = epoch;
        }
        
        public int getTimestampBit() {
            return timestampBit;
        }
        
        public void setTimestampBit(int timestampBit) {
            this.timestampBit = timestampBit;
        }
        
        public int getSequenceBit() {
            return sequenceBit;
        }
        
        public void setSequenceBit(int sequenceBit) {
            this.sequenceBit = sequenceBit;
        }
        
        public long getSequenceResetThreshold() {
            return sequenceResetThreshold;
        }
        
        public void setSequenceResetThreshold(long sequenceResetThreshold) {
            this.sequenceResetThreshold = sequenceResetThreshold;
        }
        
        public IdConverterDefinition getConverter() {
            return converter;
        }
        
        public void setConverter(IdConverterDefinition converter) {
            this.converter = converter;
        }
        
        public enum TimestampUnit {
            SECOND,
            MILLISECOND
        }
    }
    
}
