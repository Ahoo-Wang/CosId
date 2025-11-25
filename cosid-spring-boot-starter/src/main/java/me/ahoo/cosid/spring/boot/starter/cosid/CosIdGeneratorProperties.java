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

/**
 * Configuration properties for CosId generators in Spring Boot applications.
 *
 * <p>This class defines the configuration properties used to customize CosId generator behavior.
 * Properties can be configured via application.properties or application.yml using the prefix "cosid.generator".</p>
 *
 * <p>The properties control various aspects of ID generation including:
 * <ul>
 *   <li>Generator type (Radix62, Radix36, Friendly)</li>
 *   <li>Bit allocation for timestamp, machine ID, and sequence</li>
 *   <li>Sequence reset threshold</li>
 *   <li>Time zone for friendly format</li>
 *   <li>Padding behavior for friendly format</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>{@code
 * cosid:
 *   generator:
 *     enabled: true
 *     type: RADIX62
 *     namespace: myapp
 *     machine-bit: 10
 *     timestamp-bit: 41
 *     sequence-bit: 12
 *     sequence-reset-threshold: 1000
 *     zone-id: UTC
 *     pad-start: true
 * }</pre>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = CosIdGeneratorProperties.PREFIX)
public class CosIdGeneratorProperties {
    /**
     * The configuration property prefix for CosId generator properties.
     */
    public static final String PREFIX = CosId.COSID_PREFIX + "generator";

    /**
     * Whether CosId generator auto-configuration is enabled.
     * Default is false.
     */
    private boolean enabled = false;

    /**
     * The type of CosId generator to use.
     * Default is RADIX62.
     */
    private Type type = Type.RADIX62;

    /**
     * The namespace for ID generation.
     * If not specified, uses the global CosId namespace.
     */
    private String namespace;

    /**
     * Number of bits allocated for machine ID in the generated IDs.
     * Default is {@link RadixCosIdGenerator#DEFAULT_MACHINE_BIT}.
     */
    private int machineBit = DEFAULT_MACHINE_BIT;

    /**
     * Number of bits allocated for timestamp in the generated IDs.
     * Default is {@link RadixCosIdGenerator#DEFAULT_TIMESTAMP_BIT}.
     */
    private int timestampBit = RadixCosIdGenerator.DEFAULT_TIMESTAMP_BIT;

    /**
     * Number of bits allocated for sequence number in the generated IDs.
     * Default is {@link RadixCosIdGenerator#DEFAULT_SEQUENCE_BIT}.
     */
    private int sequenceBit = RadixCosIdGenerator.DEFAULT_SEQUENCE_BIT;

    /**
     * The threshold for resetting the sequence counter when it reaches this value.
     * Default is {@link RadixCosIdGenerator#DEFAULT_SEQUENCE_RESET_THRESHOLD}.
     */
    private int sequenceResetThreshold = RadixCosIdGenerator.DEFAULT_SEQUENCE_RESET_THRESHOLD;

    /**
     * The time zone to use for friendly format generators.
     * Default is the system default time zone.
     */
    private ZoneId zoneId = ZoneId.systemDefault();

    /**
     * Whether to pad the start of friendly format IDs with zeros.
     * Default is true.
     */
    private boolean padStart = true;

    /**
     * Checks if CosId generator auto-configuration is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether CosId generator auto-configuration should be enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the namespace for ID generation.
     *
     * @return the namespace string, or null if not set
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace for ID generation.
     *
     * @param namespace the namespace string to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the number of bits allocated for machine ID.
     *
     * @return the machine bit count
     */
    public int getMachineBit() {
        return machineBit;
    }

    /**
     * Sets the number of bits allocated for machine ID.
     *
     * @param machineBit the machine bit count to set
     */
    public void setMachineBit(int machineBit) {
        this.machineBit = machineBit;
    }

    /**
     * Gets the number of bits allocated for timestamp.
     *
     * @return the timestamp bit count
     */
    public int getTimestampBit() {
        return timestampBit;
    }

    /**
     * Sets the number of bits allocated for timestamp.
     *
     * @param timestampBit the timestamp bit count to set
     */
    public void setTimestampBit(int timestampBit) {
        this.timestampBit = timestampBit;
    }

    /**
     * Gets the number of bits allocated for sequence number.
     *
     * @return the sequence bit count
     */
    public int getSequenceBit() {
        return sequenceBit;
    }

    /**
     * Sets the number of bits allocated for sequence number.
     *
     * @param sequenceBit the sequence bit count to set
     */
    public void setSequenceBit(int sequenceBit) {
        this.sequenceBit = sequenceBit;
    }

    /**
     * Gets the sequence reset threshold.
     *
     * @return the sequence reset threshold value
     */
    public int getSequenceResetThreshold() {
        return sequenceResetThreshold;
    }

    /**
     * Sets the sequence reset threshold.
     *
     * @param sequenceResetThreshold the sequence reset threshold to set
     */
    public void setSequenceResetThreshold(int sequenceResetThreshold) {
        this.sequenceResetThreshold = sequenceResetThreshold;
    }

    /**
     * Gets the time zone for friendly format generators.
     *
     * @return the zone ID
     */
    public ZoneId getZoneId() {
        return zoneId;
    }

    /**
     * Sets the time zone for friendly format generators.
     *
     * @param zoneId the zone ID to set
     */
    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Checks if padding is enabled for friendly format IDs.
     *
     * @return true if padding is enabled, false otherwise
     */
    public boolean isPadStart() {
        return padStart;
    }

    /**
     * Sets whether to pad the start of friendly format IDs.
     *
     * @param padStart true to enable padding, false to disable
     */
    public void setPadStart(boolean padStart) {
        this.padStart = padStart;
    }

    /**
     * Gets the type of CosId generator.
     *
     * @return the generator type
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the type of CosId generator.
     *
     * @param type the generator type to set
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Enumeration of supported CosId generator types.
     */
    public enum Type {
        /**
         * Radix62 generator using base62 encoding (0-9, A-Z, a-z).
         * Produces compact alphanumeric IDs.
         */
        RADIX62,

        /**
         * Radix36 generator using base36 encoding (0-9, A-Z).
         * Produces uppercase alphanumeric IDs.
         */
        RADIX36,

        /**
         * Friendly generator that produces human-readable IDs with timestamp formatting.
         * Includes date/time components for better readability.
         */
        FRIENDLY
    }
}
