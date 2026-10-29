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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.time.UuidV7Generator;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * {@link GeneratorSpec.UuidV7} in a runtime (D46): no machine, store nor lease; its text by {@code text(name)}, its
 * {@link UUID}s by {@code get(name, UuidV7Generator.class)}.
 */
class UuidV7RuntimeTest {
    static final class Order {
        @CosId("order")
        String id;
    }

    @Test
    void aRuntimeOfUuidV7GeneratorsAloneStartsWithoutAMachineOrStore() {
        RuntimeSpec spec = RuntimeSpec.builder("app").generator("order", GeneratorSpec.UuidV7.builder().build()).build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options().toBuilder().countGenerated(true).build())) {
            assertTrue(runtime.machines().isEmpty(), "no machine starts");
            assertTrue(runtime.slot().isEmpty());

            String text = runtime.text("order").get();
            assertEquals(7, UUID.fromString(text).version());
            UuidV7Generator generator = runtime.get("order", UuidV7Generator.class);
            UUID id = generator.generateUuid();
            assertThat(id.compareTo(UUID.fromString(text)), is(1));

            Order order = new Order();
            runtime.filler().fill(order);
            assertEquals(2, UUID.fromString(order.id).variant());
            assertNotEquals(text, order.id);

            GeneratorInfo info = runtime.describe().get("order");
            assertEquals("uuidv7", info.strategy());
            assertEquals("", info.store());
            assertEquals(7, info.details().get("version"));
            // The text view and the filler are counted; the generator itself, by its concrete type, is not.
            assertEquals(OptionalLong.of(2), runtime.generated("order"));
        }
    }

    @Test
    void issuesNoLongIdsAsCosId80Does() {
        RuntimeSpec spec = RuntimeSpec.builder("app").generator("order", GeneratorSpec.UuidV7.builder().build()).build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options())) {
            assertThat(assertThrows(NoSuchElementException.class, () -> runtime.generator("order")).getMessage(),
                containsString("(uuidv7) issues no long ids"));
            assertThrows(NoSuchElementException.class, () -> runtime.stringGenerator("order"));
        }
    }

    @Test
    void aDisabledUuidV7GeneratorIsDescribed() {
        RuntimeSpec spec = RuntimeSpec.builder("app").generator("off", new GeneratorSpec.Disabled(GeneratorSpec.UuidV7.builder().build())).build();
        try (CosIdRuntime runtime = CosIdRuntime.start(spec, options())) {
            assertEquals("uuidv7", runtime.describe().get("off").strategy());
        }
    }

    @Test
    void theSpecIsAValue() {
        assertEquals(GeneratorSpec.UuidV7.builder().build(), GeneratorSpec.UuidV7.builder().build());
        assertEquals(GeneratorSpec.UuidV7.builder().build().hashCode(), GeneratorSpec.UuidV7.builder().build().hashCode());
        assertEquals("UuidV7[]", GeneratorSpec.UuidV7.builder().build().toString());
    }
}
