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

package me.ahoo.cosid.string;

import me.ahoo.cosid.converter.Radix62IdConverter;

public final class CosIdIdStateParser {
    private final Radix62IdConverter timestampConvert;
    private final Radix62IdConverter machineConvert;
    private final Radix62IdConverter sequenceConvert;
    
    public CosIdIdStateParser(Radix62IdConverter timestampConvert, Radix62IdConverter machineConvert, Radix62IdConverter sequenceConvert) {
        this.timestampConvert = timestampConvert;
        this.machineConvert = machineConvert;
        this.sequenceConvert = sequenceConvert;
    }
    
    public CosIdState asState(String id) {
        String timestampPart = id.substring(0, timestampConvert.getCharSize());
        String machineIdPart = id.substring(timestampConvert.getCharSize(), timestampConvert.getCharSize() + machineConvert.getCharSize());
        String sequencePart = id.substring(timestampConvert.getCharSize() + machineConvert.getCharSize());
        return new CosIdState(timestampConvert.asLong(timestampPart), (int) machineConvert.asLong(machineIdPart), (int) sequenceConvert.asLong(sequencePart));
    }
    
    public String asString(CosIdState cosIdState) {
        return asString(cosIdState.getTimestamp(), cosIdState.getMachineId(), cosIdState.getSequence());
    }
    
    public String asString(long lastTimestamp, int machineId, int sequence) {
        return timestampConvert.asString(lastTimestamp) + machineConvert.asString(machineId) + sequenceConvert.asString(sequence);
    }
    
}
