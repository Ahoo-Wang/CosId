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

import java.time.ZoneId;

/**
 * CosIdGenerator that produces human-readable string IDs.
 *
 * <p>Extends RadixCosIdGenerator with a FriendlyIdStateParser that generates
 * IDs in format: {@code yyyyMMddHHmmssSSS-machineId-sequence}
 *
 * @author ahoo wang
 */
public class FriendlyCosIdGenerator extends RadixCosIdGenerator {

    /**
     * Creates a generator with default bit configuration.
     *
     * @param machineId the machine ID
     * @param zoneId    time zone for timestamp formatting
     * @param padStart  whether to pad numbers with leading zeros
     */
    public FriendlyCosIdGenerator(int machineId, ZoneId zoneId, boolean padStart) {
        this(DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD, zoneId, padStart);
    }

    /**
     * Creates a generator with custom bit configuration.
     *
     * @param timestampBit           number of bits for timestamp
     * @param machineIdBit          number of bits for machine ID
     * @param sequenceBit           number of bits for sequence
     * @param machineId             the machine ID
     * @param sequenceResetThreshold threshold for resetting sequence
     * @param zoneId                time zone for timestamp formatting
     * @param padStart              whether to pad numbers with leading zeros
     */
    public FriendlyCosIdGenerator(int timestampBit, int machineIdBit, int sequenceBit, int machineId, int sequenceResetThreshold, ZoneId zoneId, boolean padStart) {
        super(timestampBit, machineIdBit, sequenceBit, machineId, sequenceResetThreshold, new FriendlyIdStateParser(zoneId, padStart, machineIdBit, sequenceBit));
    }

}
