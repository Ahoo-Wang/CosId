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

package me.ahoo.cosid.spring.boot.starter.cosid;

import static me.ahoo.cosid.cosid.RadixCosIdGenerator.DEFAULT_MACHINE_BIT;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.cosid.RadixCosIdGenerator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = CosIdGeneratorProperties.PREFIX)
public class CosIdGeneratorProperties {
    public static final String PREFIX = CosId.COSID_PREFIX + "generator";
    private boolean enabled = false;
    private Type type = Type.RADIX62;
    private String namespace;
    private int machineBit = DEFAULT_MACHINE_BIT;
    private int timestampBit = RadixCosIdGenerator.DEFAULT_TIMESTAMP_BIT;
    private int sequenceBit = RadixCosIdGenerator.DEFAULT_SEQUENCE_BIT;
    private int sequenceResetThreshold = RadixCosIdGenerator.DEFAULT_SEQUENCE_RESET_THRESHOLD;
    private ZoneId zoneId = ZoneId.systemDefault();
    private boolean padStart = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getMachineBit() {
        return machineBit;
    }

    public void setMachineBit(int machineBit) {
        this.machineBit = machineBit;
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

    public int getSequenceResetThreshold() {
        return sequenceResetThreshold;
    }

    public void setSequenceResetThreshold(int sequenceResetThreshold) {
        this.sequenceResetThreshold = sequenceResetThreshold;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public boolean isPadStart() {
        return padStart;
    }

    public void setPadStart(boolean padStart) {
        this.padStart = padStart;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public enum Type {
        RADIX62,
        RADIX36,
        FRIENDLY
    }
}
