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

import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.entity.LongIdEntity;
import me.ahoo.cosid.annotation.entity.MissingIdGenEntity;
import me.ahoo.cosid.annotation.entity.StringIdEntity;
import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.NotFoundIdGeneratorException;

import com.google.common.base.Strings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class DefaultAccessorRegistryTest {

    CosIdAccessorRegistry accessorRegistry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE));

    @Test
    void ensureIdExistsByAnnotation() {
        DefaultIdGeneratorProvider.INSTANCE.setShare(AtomicLongGenerator.INSTANCE);
        LongIdEntity entity = new LongIdEntity();
        entity.setId(888L);
        accessorRegistry.ensureId(entity);
        Assertions.assertEquals(888, entity.getId());
    }

    @Test
    void ensureIdNotFindByAnnotation() {
        MissingIdGenEntity entity = new MissingIdGenEntity();
        Assertions.assertThrows(NotFoundIdGeneratorException.class, () -> {
            accessorRegistry.ensureId(entity);
        });
    }

    @Test
    void ensureStringId() {
        DefaultIdGeneratorProvider.INSTANCE.setShare(AtomicLongGenerator.INSTANCE);
        StringIdEntity entity = new StringIdEntity();
        accessorRegistry.ensureId(entity);
        Assertions.assertFalse(Strings.isNullOrEmpty(entity.getId()));
    }

}
