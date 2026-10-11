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

package me.ahoo.cosid.accessor.registry;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.jvm.UuidGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StringOnlyGeneratorValidationTest {
    private static final String UUID = "uuid";

    @Test
    void registerFailsWhenLongFieldUsesStringOnlyGenerator() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.set(UUID, UuidGenerator.INSTANCE);
        DefaultAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE, provider));

        CosIdException exception = Assertions.assertThrows(CosIdException.class, () -> registry.register(LongUuidEntity.class));

        Assertions.assertTrue(exception.getMessage().contains(LongUuidEntity.class.getName() + ".id"), exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("[" + UUID + "]"), exception.getMessage());
    }

    @Test
    void validateFailsOnceTheStringOnlyGeneratorIsRegistered() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        DefaultAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE, provider));
        registry.register(LongUuidEntity.class);
        registry.register(StringUuidEntity.class);

        provider.set(UUID, UuidGenerator.INSTANCE);

        Assertions.assertThrows(CosIdException.class, registry::validate);
    }

    @Test
    void fillFailsNamingTheField() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        DefaultAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE, provider));
        registry.register(LongUuidEntity.class);
        provider.set(UUID, UuidGenerator.INSTANCE);

        CosIdException exception = Assertions.assertThrows(CosIdException.class, () -> registry.ensureId(new LongUuidEntity()));

        Assertions.assertTrue(exception.getMessage().contains(LongUuidEntity.class.getName() + ".id"), exception.getMessage());
    }

    @Test
    void stringFieldWorksWithStringOnlyGenerator() {
        DefaultIdGeneratorProvider provider = new DefaultIdGeneratorProvider();
        provider.set(UUID, UuidGenerator.INSTANCE);
        DefaultAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE, provider));
        registry.register(StringUuidEntity.class);
        registry.validate();

        StringUuidEntity entity = new StringUuidEntity();
        Assertions.assertTrue(registry.ensureId(entity));
        Assertions.assertNotNull(entity.id);
    }

    static class LongUuidEntity {
        @CosId(UUID)
        long id;
    }

    static class StringUuidEntity {
        @CosId(UUID)
        String id;
    }
}
