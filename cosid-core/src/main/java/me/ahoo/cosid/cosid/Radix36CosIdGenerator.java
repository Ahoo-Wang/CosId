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

/**
 * [timestamp(44)]-[machineId-(20)]-[sequence-(16)] = 80 BITS = 17 CHARS.
 */
public class Radix36CosIdGenerator extends RadixCosIdGenerator {

    public Radix36CosIdGenerator(int machineId) {
        this(DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, machineId, DEFAULT_SEQUENCE_RESET_THRESHOLD);
    }
    
    public Radix36CosIdGenerator(int timestampBit, int machineIdBit, int sequenceBit, int machineId, int sequenceResetThreshold) {
        super(timestampBit, machineIdBit, sequenceBit, machineId, sequenceResetThreshold, RadixCosIdStateParser.ofRadix36(timestampBit, machineIdBit, sequenceBit));
    }
    
}
