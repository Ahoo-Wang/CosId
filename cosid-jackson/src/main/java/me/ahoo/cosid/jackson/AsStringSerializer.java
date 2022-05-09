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

package me.ahoo.cosid.jackson;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * AsString Serializer.
 *
 * @author ahoo wang
 */
public class AsStringSerializer extends JsonSerializer<Long> implements ContextualSerializer {
    
    private static final AsStringSerializer TO_STRING = new AsStringSerializer();
    
    private static final AsStringSerializer DEFAULT_RADIX = new AsStringSerializer(Radix62IdConverter.INSTANCE);
    private static final AsStringSerializer DEFAULT_RADIX_PAD_START = new AsStringSerializer(Radix62IdConverter.PAD_START);
    private static final AsStringSerializer DEFAULT_FRIENDLY_ID = new AsStringSerializer(SnowflakeFriendlyIdConverter.INSTANCE);
    
    private final IdConverter converter;
    
    public AsStringSerializer() {
        this(ToStringIdConverter.INSTANCE);
    }
    
    public AsStringSerializer(IdConverter converter) {
        this.converter = converter;
    }
    
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        AsString asString = property.getAnnotation(AsString.class);
        switch (asString.value()) {
            case TO_STRING: {
                return TO_STRING;
            }
            case RADIX: {
                if (Radix62IdConverter.MAX_CHAR_SIZE != asString.radixCharSize()) {
                    IdConverter idConverter = Radix62IdConverter.of(asString.radixPadStart(), asString.radixCharSize());
                    return new AsStringSerializer(idConverter);
                }
                return asString.radixPadStart() ? DEFAULT_RADIX_PAD_START : DEFAULT_RADIX;
            }
            case FRIENDLY_ID: {
                if (isDefaultSnowflakeFriendlyIdConverter(asString)) {
                    return DEFAULT_FRIENDLY_ID;
                }
                SnowflakeIdStateParser stateParser = new MillisecondSnowflakeIdStateParser(asString.epoch(), asString.timestampBit(), asString.machineBit(), asString.sequenceBit());
                IdConverter idConverter = new SnowflakeFriendlyIdConverter(stateParser);
                return new AsStringSerializer(idConverter);
            }
            default:
                throw new IllegalStateException("Unexpected value: " + asString.value());
        }
    }
    
    static boolean isDefaultSnowflakeFriendlyIdConverter(AsString asString) {
        return CosId.COSID_EPOCH == asString.epoch()
            && MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT == asString.timestampBit()
            && MillisecondSnowflakeId.DEFAULT_MACHINE_BIT == asString.machineBit()
            && MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT == asString.sequenceBit();
    }
    
    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (Objects.isNull(value)) {
            gen.writeNull();
        } else {
            String valueStr = converter.asString(value);
            gen.writeString(valueStr);
        }
    }
}
