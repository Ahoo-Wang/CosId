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
import me.ahoo.cosid.accessor.parser.DefaultAccessorParser;
import me.ahoo.cosid.accessor.parser.NamedDefinitionParser;
import me.ahoo.cosid.accessor.registry.CosIdAccessorRegistry;
import me.ahoo.cosid.accessor.registry.DefaultAccessorRegistry;
import me.ahoo.cosid.accessor.scanner.entity.OrderEntity;
import me.ahoo.cosid.accessor.scanner.entity.OrderItemEntity;
import me.ahoo.cosid.annotation.AnnotationDefinitionParser;
import me.ahoo.cosid.annotation.entity.ChildEntity;
import me.ahoo.cosid.annotation.entity.IntIdEntity;
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
        
        assertAccessor(registry, LongIdEntity.class, LongIdEntity.class);
        assertAccessor(registry, MissingIdGenEntity.class, MissingIdGenEntity.class);
        assertAccessor(registry, PrimitiveLongIdEntity.class, PrimitiveLongIdEntity.class);
        assertAccessor(registry, IntIdEntity.class, IntIdEntity.class);
        assertAccessor(registry, StringIdEntity.class, StringIdEntity.class);
        assertAccessor(registry, ChildEntity.class, LongIdEntity.class);
    }
    
    @Test
    void scanNamedDefinitionParser() {
        CosIdAccessorRegistry registry = new DefaultAccessorRegistry(new DefaultAccessorParser(AnnotationDefinitionParser.INSTANCE));
        DefaultCosIdScanner scanner =
            new DefaultCosIdScanner(new String[] {"me.ahoo.cosid.accessor.scanner.entity"}, new NamedDefinitionParser("id"), registry);
        scanner.scan();
        
        assertAccessor(registry, OrderEntity.class, OrderEntity.class);
        assertAccessor(registry, OrderItemEntity.class, OrderItemEntity.class);
    }

    private static void assertAccessor(CosIdAccessorRegistry registry, Class<?> lookupClass, Class<?> idDeclaringClass) {
        CosIdAccessor cosIdAccessor = registry.get(lookupClass);
        Assertions.assertNotEquals(CosIdAccessor.NOT_FOUND, cosIdAccessor);
        Assertions.assertEquals(idDeclaringClass, cosIdAccessor.getIdDeclaringClass());
    }
}
