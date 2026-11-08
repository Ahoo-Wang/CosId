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

import me.ahoo.cosid.codec.IdCodec;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.util.AccessPattern;

import java.util.Optional;
import java.util.function.Function;

/**
 * Reads an {@link AsString} id. A JSON string is decoded, an empty one is {@code null}; a JSON number is the id itself if
 * the codec could have written it. Text the codec cannot read is reported through
 * {@link DeserializationContext#handleWeirdStringValue}, an {@code InvalidFormatException} (a client error) by default.
 * A {@code null} (or empty text) for a {@code long} follows {@link DeserializationFeature#FAIL_ON_NULL_FOR_PRIMITIVES}:
 * an input error when it is enabled, 0 otherwise, as Jackson reads a plain {@code long}.
 */
public class AsStringDeserializer extends ValueDeserializer<Long> {
    private final Optional<Function<String, IdCodec>> generators;
    private final IdCodec codec;
    private final boolean primitive;

    /** A deserializer of decimal text, without a runtime: what {@code @JsonDeserialize(using = ...)} creates. */
    public AsStringDeserializer() {
        this(Optional.empty(), AsStringCodecs.TO_STRING, false);
    }

    AsStringDeserializer(Optional<Function<String, IdCodec>> generators, IdCodec codec, boolean primitive) {
        this.generators = generators;
        this.codec = codec;
        this.primitive = primitive;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        AsString asString = property == null ? null : property.getAnnotation(AsString.class);
        if (asString == null) {
            return this;
        }
        try {
            return new AsStringDeserializer(generators, AsStringCodecs.of(asString, generators), property.getType().isPrimitive());
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
            return getNullValue(ctxt);
        }
        try {
            return codec.decode(text);
        } catch (RuntimeException invalid) {
            return (Long) ctxt.handleWeirdStringValue(Long.class, text, "not a valid id: %s", invalid.getMessage());
        }
    }

    /**
     * Reads an id with its type id ({@code @JsonTypeInfo}). The type id of a {@code Long} is metadata only: the value is
     * read with the codec, not with the deserializer the type id names, which knows no codec (#1460). A scalar's type id
     * wraps it in an array ({@code WRAPPER_ARRAY}, and {@code PROPERTY}, which a scalar falls back to) or an object
     * ({@code WRAPPER_OBJECT}); with any other inclusion the value stands alone.
     */
    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) {
        JsonToken token = p.currentToken();
        boolean array = token == JsonToken.START_ARRAY;
        if (!array && !(token == JsonToken.START_OBJECT && typeDeserializer.getTypeInclusion() == JsonTypeInfo.As.WRAPPER_OBJECT)) {
            return deserialize(p, ctxt);
        }
        JsonToken typeId = p.nextToken();
        if (typeId != (array ? JsonToken.VALUE_STRING : JsonToken.PROPERTY_NAME)) {
            ctxt.reportWrongTokenException(this, array ? JsonToken.VALUE_STRING : JsonToken.PROPERTY_NAME,
                "need the type id of the id before its value");
        }
        Long value = p.nextToken() == JsonToken.VALUE_NULL ? getNullValue(ctxt) : deserialize(p, ctxt);
        JsonToken end = array ? JsonToken.END_ARRAY : JsonToken.END_OBJECT;
        if (p.nextToken() != end) {
            ctxt.reportWrongTokenException(this, end, "need the end of the type id wrapper after the id");
        }
        return value;
    }

    /**
     * {@code null} for a {@code Long}; for a {@code long}, an input error when
     * {@link DeserializationFeature#FAIL_ON_NULL_FOR_PRIMITIVES} is enabled, 0 otherwise (#1439).
     */
    @Override
    public Long getNullValue(DeserializationContext ctxt) {
        if (!primitive) {
            return null;
        }
        if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
            return ctxt.reportInputMismatch(this,
                "Cannot map `null` into type long (set DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)");
        }
        return 0L;
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return primitive ? AccessPattern.DYNAMIC : AccessPattern.ALWAYS_NULL;
    }

    @Override
    public Class<?> handledType() {
        return Long.class;
    }
}
