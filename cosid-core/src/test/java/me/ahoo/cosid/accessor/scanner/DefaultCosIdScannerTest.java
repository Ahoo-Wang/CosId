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

package me.ahoo.cosid.accessor.scanner;

import me.ahoo.cosid.accessor.CosIdAccessor;
import me.ahoo.cosid.accessor.parser.CosIdAccessorParser;
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.parser.NamedDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.accessor.scanner.entity.OrderEntity;
import me.ahoo.cosid.accessor.scanner.entity.OrderItemEntity;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.entity.ChildEntity;
import me.ahoo.cosid.annotation.entity.LongIdEntity;
import me.ahoo.cosid.annotation.entity.MissingIdGenEntity;
import me.ahoo.cosid.annotation.entity.PrimitiveLongIdEntity;
import me.ahoo.cosid.annotation.entity.StringIdEntity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class DefaultCosIdScannerTest {

    @Test
    void scanAnnotationDefinitionParser() {
        CosIdAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE));
        DefaultCosIdScanner scanner =
            new DefaultCosIdScanner(new String[] {"me.ahoo.cosid.accessor.annotation.entity"}, AnnotationDefinitionParser.INSTANCE, registry);
        scanner.scan();

        CosIdAccessor cosIdAccessor = registry.get(LongIdEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(LongIdEntity.class, cosIdAccessor.getIdDeclaringClass());

        cosIdAccessor = registry.get(MissingIdGenEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(MissingIdGenEntity.class, cosIdAccessor.getIdDeclaringClass());

        cosIdAccessor = registry.get(PrimitiveLongIdEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(PrimitiveLongIdEntity.class, cosIdAccessor.getIdDeclaringClass());

        cosIdAccessor = registry.get(StringIdEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(StringIdEntity.class, cosIdAccessor.getIdDeclaringClass());

        cosIdAccessor = registry.get(ChildEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(LongIdEntity.class, cosIdAccessor.getIdDeclaringClass());
    }

    @Test
    void scanNamedDefinitionParser() {
        CosIdAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE));
        DefaultCosIdScanner scanner =
            new DefaultCosIdScanner(new String[] {"me.ahoo.cosid.accessor.scanner.entity"}, new NamedDefinitionParser("id"), registry);
        scanner.scan();

        CosIdAccessor cosIdAccessor = registry.get(OrderEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(OrderEntity.class, cosIdAccessor.getIdDeclaringClass());

        cosIdAccessor = registry.get(OrderItemEntity.class);
        Assertions.assertNotNull(cosIdAccessor);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(OrderItemEntity.class, cosIdAccessor.getIdDeclaringClass());
    }

}
