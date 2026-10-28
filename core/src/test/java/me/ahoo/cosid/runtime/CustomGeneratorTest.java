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

package me.ahoo.cosid.runtime;

import static me.ahoo.cosid.runtime.CosIdRuntimeTest.options;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.observe.GeneratorInfo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link GeneratorSpec.Custom} and {@link GeneratorSpec.Instance} (D41): user {@code long} generators the runtime hands
 * out but does not own.
 */
class CustomGeneratorTest {
    /** A user generator that tells whether anyone closed it. */
    static final class Legacy implements IdGenerator, AutoCloseable {
        final AtomicLong next = new AtomicLong(1000);
        boolean closed;

        @Override
        public long generate() {
            return next.getAndIncrement();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static final class LongEntity {
        @CosId("legacy")
        Long id;
    }

    static final class TextEntity {
        @CosId("text")
        String id;
    }

    @Test
    void anInstanceTakesPartInEveryLookupAndStaysOpen() {
        Legacy legacy = new Legacy();
        RuntimeSpec spec = RuntimeSpec.builder("app")
            .generator("legacy", GeneratorSpec.Instance.builder(legacy).build())
            .generator("text", GeneratorSpec.Instance.builder(legacy).codec(List.of(new CodecSpec.Radix(10, 6, true), new CodecSpec.Prefix("L"))).build())
            .generator("off", new GeneratorSpec.Disabled(GeneratorSpec.Instance.builder(legacy).build()))
            .build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options().toBuilder().countGenerated(true).build())) {
            assertThat(runtime.generator("legacy").generate(), is(1000L));
            assertThat(runtime.stringGenerator("text").generate(), is("L001001"));
            assertThat(runtime.get("legacy", Legacy.class), sameInstance(legacy));
            LongEntity entity = new LongEntity();
            runtime.filler().fill(entity);
            assertThat(entity.id, is(1002L));
            TextEntity text = new TextEntity();
            runtime.filler().fill(text);
            assertThat(text.id, is("L001003"));

            GeneratorInfo info = runtime.describe().get("legacy");
            assertThat(info.strategy(), is("custom"));
            assertThat(info.store(), is(""));
            assertThat(info.details(), is(Map.of("class", Legacy.class.getName(), "generated", 2L)));
            assertThat(runtime.generated("text"), is(OptionalLong.of(2)));
            assertThat(runtime.describe().get("off").strategy(), is("custom"));
            assertThrows(GeneratorDisabledException.class, () -> runtime.generator("off"));
        }
        assertThat(legacy.closed, is(false));
        assertThat(legacy.generate(), is(1004L));
    }

    @Test
    void aCustomGeneratorResolvesItsReference() {
        Legacy legacy = new Legacy();
        RuntimeSpec spec = RuntimeSpec.builder("app")
            .generator(RuntimeSpec.DEFAULT_GENERATOR, GeneratorSpec.Custom.builder("legacyIds").codec(List.of(new CodecSpec.Radix(62, 4, true))).build())
            .build();
        RuntimeOptions options = options().toBuilder()
            .generators(reference -> "legacyIds".equals(reference) ? Optional.<IdGenerator>of(legacy) : Optional.empty())
            .build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options)) {
            assertThat(runtime.generator(RuntimeSpec.DEFAULT_GENERATOR).generate(), is(1000L));
            assertThat(runtime.stringGenerator(RuntimeSpec.DEFAULT_GENERATOR).generate(), is("00G9"));
            assertThat(runtime.describe().get(RuntimeSpec.DEFAULT_GENERATOR).details().get("class"), is(Legacy.class.getName()));
            assertThat(runtime.slot(), is(Optional.empty()));
        }
        assertThat(legacy.closed, is(false));
    }

    @Test
    void theDefaultOptionsResolveNoReference() {
        assertThat(RuntimeOptions.defaults().generators().apply("anything"), is(Optional.empty()));
    }
}
