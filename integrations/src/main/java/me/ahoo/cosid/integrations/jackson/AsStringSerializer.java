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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.Optional;
import java.util.function.Function;

/**
 * Writes an {@link AsString} id as text. Without a property (a root value, a container element) it writes decimal text.
 */
public class AsStringSerializer extends ValueSerializer<Long> {
    private final Optional<Function<String, IdCodec>> generators;
    private final IdCodec codec;

    /** A serializer of decimal text, without a runtime: what {@code @JsonSerialize(using = ...)} creates. */
    public AsStringSerializer() {
        this(Optional.empty(), AsStringCodecs.TO_STRING);
    }

    AsStringSerializer(Optional<Function<String, IdCodec>> generators, IdCodec codec) {
        this.generators = generators;
        this.codec = codec;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        AsString asString = property == null ? null : property.getAnnotation(AsString.class);
        if (asString == null) {
            return this;
        }
        try {
            return new AsStringSerializer(generators, AsStringCodecs.of(asString, generators));
        } catch (RuntimeException invalid) {
            return ctxt.reportBadDefinition(property.getType(), "Invalid @AsString on " + property.getName() + ": " + invalid.getMessage(), invalid);
        }
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(codec.encode(value));
    }

    @Override
    public Class<?> handledType() {
        return Long.class;
    }
}
