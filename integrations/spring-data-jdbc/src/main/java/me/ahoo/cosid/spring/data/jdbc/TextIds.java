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


package me.ahoo.cosid.spring.data.jdbc;

import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.runtime.CosIdRuntime;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The text ids of a runtime's generator, whatever its strategy: a {@code long} generator through its codec, the 80-bit
 * CosId through its {@code PartsCodec}, a periodic segment through its {@code GroupedCodec} (the text {@code IdFiller}
 * puts in a {@code String} field). The generator is looked up on the first id, so a disabled one fails when used (D24),
 * not when the integration is set up.
 */
final class TextIds implements Supplier<String> {
    private final CosIdRuntime runtime;
    private final String generator;
    private volatile Supplier<String> resolved;

    private TextIds(CosIdRuntime runtime, String generator) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    /**
     * The text ids of a generator.
     *
     * @param runtime   the runtime
     * @param generator the generator name
     * @return the text ids
     */
    static TextIds of(CosIdRuntime runtime, String generator) {
        return new TextIds(runtime, generator);
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    String generator() {
        return generator;
    }

    /**
     * The next text id.
     *
     * @return the id
     * @throws NoSuchElementException                    if the runtime has no such generator
     * @throws me.ahoo.cosid.GeneratorDisabledException if it is disabled
     */
    @Override
    public String get() {
        Supplier<String> ids = resolved;
        if (ids == null) {
            ids = resolve(runtime, generator);
            resolved = ids;
        }
        return ids.get();
    }

    private static Supplier<String> resolve(CosIdRuntime runtime, String generator) {
        GeneratorInfo info = runtime.describe().get(generator);
        if (info == null) {
            throw new NoSuchElementException("No generator [" + generator + "] in namespace [" + runtime.namespace() + "]");
        }
        return switch (info.strategy()) {
            case "cosid80" -> {
                PartsIdGenerator ids = runtime.get(generator, PartsIdGenerator.class);
                PartsCodec codec = runtime.get(generator, PartsCodec.class);
                yield () -> codec.encode(ids.nextParts());
            }
            case "periodic-segment" -> {
                GroupedIdGenerator ids = runtime.get(generator, GroupedIdGenerator.class);
                GroupedCodec codec = runtime.get(generator, GroupedCodec.class);
                yield () -> codec.encode(ids.generate());
            }
            default -> runtime.get(generator, StringIdGenerator.class)::generate;
        };
    }
}
