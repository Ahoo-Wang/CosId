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

import me.ahoo.cosid.converter.Radix36IdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.RadixIdConverter;

import com.google.common.base.Preconditions;


public class RadixCosIdStateParser implements CosIdIdStateParser {
    public static final RadixCosIdStateParser DEFAULT = ofRadix62(Radix62CosIdGenerator.DEFAULT_TIMESTAMP_BIT, Radix62CosIdGenerator.DEFAULT_MACHINE_BIT, Radix62CosIdGenerator.DEFAULT_SEQUENCE_BIT);
    
    private final RadixIdConverter timestampConvert;
    private final RadixIdConverter machineConvert;
    private final RadixIdConverter sequenceConvert;
    
    public RadixCosIdStateParser(RadixIdConverter timestampConvert, RadixIdConverter machineConvert, RadixIdConverter sequenceConvert) {
        this.timestampConvert = timestampConvert;
        this.machineConvert = machineConvert;
        this.sequenceConvert = sequenceConvert;
    }
    
    @Override
    public CosIdState asState(String id) {
        int totalCharSize = timestampConvert.getCharSize() + machineConvert.getCharSize() + sequenceConvert.getCharSize();
        Preconditions.checkArgument(id.length() == totalCharSize, "id[%s] length must equal to totalCharSize:[%s].", id, totalCharSize);
        String timestampPart = id.substring(0, timestampConvert.getCharSize());
        String machineIdPart = id.substring(timestampConvert.getCharSize(), timestampConvert.getCharSize() + machineConvert.getCharSize());
        String sequencePart = id.substring(timestampConvert.getCharSize() + machineConvert.getCharSize());
        return new CosIdState(timestampConvert.asLong(timestampPart), (int) machineConvert.asLong(machineIdPart), (int) sequenceConvert.asLong(sequencePart));
    }
    
    public String asString(CosIdState cosIdState) {
        return asString(cosIdState.getTimestamp(), cosIdState.getMachineId(), cosIdState.getSequence());
    }
    
    @Override
    public String asString(long lastTimestamp, int machineId, int sequence) {
        return timestampConvert.asString(lastTimestamp) + machineConvert.asString(machineId) + sequenceConvert.asString(sequence);
    }
    
    static RadixCosIdStateParser ofRadix62(int timestampBits, int machineIdBits, int sequenceBits) {
        final int radix = 62;
        final int timestampMaxCharSize = RadixIdConverter.maxCharSize(radix, timestampBits);
        final int machineIdMaxCharSize = RadixIdConverter.maxCharSize(radix, machineIdBits);
        final int sequenceMaxCharSize = RadixIdConverter.maxCharSize(radix, sequenceBits);
        return new RadixCosIdStateParser(
            new Radix62IdConverter(true, timestampMaxCharSize),
            new Radix62IdConverter(true, machineIdMaxCharSize),
            new Radix62IdConverter(true, sequenceMaxCharSize)
        );
    }
    
    static RadixCosIdStateParser ofRadix36(int timestampBits, int machineIdBits, int sequenceBits) {
        final int radix = 36;
        final int timestampMaxCharSize = RadixIdConverter.maxCharSize(radix, timestampBits);
        final int machineIdMaxCharSize = RadixIdConverter.maxCharSize(radix, machineIdBits);
        final int sequenceMaxCharSize = RadixIdConverter.maxCharSize(radix, sequenceBits);
        return new RadixCosIdStateParser(
            new Radix36IdConverter(true, timestampMaxCharSize),
            new Radix36IdConverter(true, machineIdMaxCharSize),
            new Radix36IdConverter(true, sequenceMaxCharSize)
        );
    }
    
}
