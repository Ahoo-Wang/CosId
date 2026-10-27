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


package me.ahoo.cosid.integrations.jackson;

import me.ahoo.cosid.codec.IdCodec;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.Optional;
import java.util.function.Function;

/**
 * Reads an {@link AsString} id. A JSON string is decoded, an empty one is {@code null}; a JSON number is the id itself if
 * the codec could have written it. Text the codec cannot read is reported through
 * {@link DeserializationContext#handleWeirdStringValue}, an {@code InvalidFormatException} (a client error) by default.
 */
public class AsStringDeserializer extends ValueDeserializer<Long> {
    private final Optional<Function<String, IdCodec>> generators;
    private final IdCodec codec;

    /** A deserializer of decimal text, without a runtime: what {@code @JsonDeserialize(using = ...)} creates. */
    public AsStringDeserializer() {
        this(Optional.empty(), AsStringCodecs.TO_STRING);
    }

    AsStringDeserializer(Optional<Function<String, IdCodec>> generators, IdCodec codec) {
        this.generators = generators;
        this.codec = codec;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        AsString asString = property == null ? null : property.getAnnotation(AsString.class);
        if (asString == null) {
            return this;
        }
        try {
            return new AsStringDeserializer(generators, AsStringCodecs.of(asString, generators));
        } catch (RuntimeException invalid) {
            return ctxt.reportBadDefinition(property.getType(), "Invalid @AsString on " + property.getName() + ": " + invalid.getMessage());
        }
    }

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            long id = p.getLongValue();
            String problem;
            try {
                if (codec.decode(codec.encode(id)) == id) {
                    return id;
                }
                problem = "it does not read back as the same id";
            } catch (RuntimeException outOfRange) {
                problem = outOfRange.getMessage();
            }
            return (Long) ctxt.handleWeirdNumberValue(Long.class, id, "not a valid id: %s", problem);
        }
        if (token != JsonToken.VALUE_STRING) {
            return (Long) ctxt.handleUnexpectedToken(Long.class, p);
        }
        String text = p.getString();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return codec.decode(text);
        } catch (RuntimeException invalid) {
            return (Long) ctxt.handleWeirdStringValue(Long.class, text, "not a valid id: %s", invalid.getMessage());
        }
    }

    @Override
    public Class<?> handledType() {
        return Long.class;
    }
}
