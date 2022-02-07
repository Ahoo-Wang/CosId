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

import static me.ahoo.cosid.jackson.AsStringSerializer.isDefaultSnowflakeFriendlyIdConverter;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.google.common.base.Strings;

import java.io.IOException;
import java.util.Objects;


/**
 * AsString Deserializer.
 *
 * @author ahoo wang
 */
public class AsStringDeserializer extends JsonDeserializer<Long> implements ContextualDeserializer {

    private static final AsStringDeserializer TO_STRING = new AsStringDeserializer();

    private static final AsStringDeserializer DEFAULT_RADIX = new AsStringDeserializer(Radix62IdConverter.INSTANCE);
    private static final AsStringDeserializer DEFAULT_RADIX_PAD_START = new AsStringDeserializer(Radix62IdConverter.PAD_START);
    private static final AsStringDeserializer DEFAULT_FRIENDLY_ID = new AsStringDeserializer(SnowflakeFriendlyIdConverter.INSTANCE);

    private final IdConverter converter;

    public AsStringDeserializer() {
        this(ToStringIdConverter.INSTANCE);
    }

    public AsStringDeserializer(IdConverter converter) {
        this.converter = converter;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        AsString asString = property.getAnnotation(AsString.class);
        if (Objects.isNull(asString)) {
            return TO_STRING;
        }
        switch (asString.value()) {
            case TO_STRING: {
                return TO_STRING;
            }
            case RADIX: {
                if (Radix62IdConverter.MAX_CHAR_SIZE != asString.radixCharSize()) {
                    IdConverter idConverter = new Radix62IdConverter(asString.radixPadStart(), asString.radixCharSize());
                    return new AsStringDeserializer(idConverter);
                }
                return asString.radixPadStart() ? DEFAULT_RADIX_PAD_START : DEFAULT_RADIX;
            }
            case FRIENDLY_ID: {
                if (isDefaultSnowflakeFriendlyIdConverter(asString)) {
                    return DEFAULT_FRIENDLY_ID;
                }
                SnowflakeIdStateParser stateParser = new MillisecondSnowflakeIdStateParser(asString.epoch(), asString.timestampBit(), asString.machineBit(), asString.sequenceBit());
                IdConverter idConverter = new SnowflakeFriendlyIdConverter(stateParser);
                return new AsStringDeserializer(idConverter);
            }
            default:
                throw new IllegalStateException("Unexpected value: " + asString.value());
        }
    }

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String valueStr = p.getValueAsString();
        if (Strings.isNullOrEmpty(valueStr)) {
            return null;
        }
        return converter.asLong(valueStr);
    }
}
