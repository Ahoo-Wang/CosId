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
import me.ahoo.cosid.runtime.CosIdRuntime;

import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Binds {@link AsString#generator()} to a runtime: {@code @AsString(generator = "order")} writes and reads with the codec
 * of the runtime's generator {@code order}. The inline codecs need no module. The Spring Boot auto-configuration
 * registers one for the context's runtime.
 */
public final class CosIdJacksonModule extends JacksonModule {
    private final Function<String, IdCodec> generators;

    /**
     * A module of a runtime's generators.
     *
     * @param runtime the runtime
     */
    public CosIdJacksonModule(CosIdRuntime runtime) {
        this(name -> runtime.get(name, IdCodec.class));
        Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * A module of codecs by generator name.
     *
     * @param generators the codec of a generator name; throws for an unknown one
     */
    public CosIdJacksonModule(Function<String, IdCodec> generators) {
        this.generators = Objects.requireNonNull(generators, "generators");
    }

    @Override
    public String getModuleName() {
        return "cosid";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.insertAnnotationIntrospector(new Introspector(Optional.of(generators)));
    }

    /** Puts the module's generators into the {@link AsString} serializers, ahead of {@code @JsonSerialize(using = ...)}. */
    private static final class Introspector extends NopAnnotationIntrospector {
        private static final long serialVersionUID = 1L;
        private final transient Optional<Function<String, IdCodec>> generators;

        Introspector(Optional<Function<String, IdCodec>> generators) {
            this.generators = generators;
        }

        @Override
        public Object findSerializer(MapperConfig<?> config, Annotated am) {
            return am.hasAnnotation(AsString.class) ? new AsStringSerializer(generators, AsStringCodecs.TO_STRING) : null;
        }

        @Override
        public Object findDeserializer(MapperConfig<?> config, Annotated am) {
            return am.hasAnnotation(AsString.class) ? new AsStringDeserializer(generators, AsStringCodecs.TO_STRING) : null;
        }
    }
}
