/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosid.annotation.accessor;

import lombok.SneakyThrows;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.annotation.IdEntity;
import me.ahoo.cosid.annotation.TestEntity;
import me.ahoo.cosid.annotation.accessor.field.FieldGetter;
import me.ahoo.cosid.annotation.accessor.field.FieldSetter;
import me.ahoo.cosid.annotation.accessor.method.MethodGetter;
import me.ahoo.cosid.annotation.accessor.method.MethodSetter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @author ahoo wang
 */
class CosIdAccessorSupportTest {

    @SneakyThrows
    @Test
    void getCosIdAccessor() {
        Map<Field, CosIdAccessor> fieldCosIdAccessorMap = CosIdAccessorSupport.getCosIdAccessor(TestEntity.class);
        DefaultCosIdAccessor stringIdAccessor = (DefaultCosIdAccessor) fieldCosIdAccessorMap.get(TestEntity.class.getDeclaredField("stringId"));
        Assertions.assertNotNull(stringIdAccessor);
        Assertions.assertTrue(stringIdAccessor.getGetter() instanceof MethodGetter);
        Assertions.assertTrue(stringIdAccessor.getSetter() instanceof MethodSetter);


        DefaultCosIdAccessor idAccessor = (DefaultCosIdAccessor) fieldCosIdAccessorMap.get(IdEntity.class.getDeclaredField("id"));
        Assertions.assertNotNull(idAccessor);
        Assertions.assertTrue(idAccessor.getGetter() instanceof MethodGetter);
        Assertions.assertTrue(idAccessor.getSetter() instanceof MethodSetter);
    }


    @Test
    void wrongIdType() {
        Assertions.assertThrows(IdTypeNotSupportException.class, () -> {
            CosIdAccessorSupport.getCosIdAccessor(WrongIdType.class);
        });
    }

    @SneakyThrows
    @Test
    void noGetterSetter() {
        Map<Field, CosIdAccessor> fieldCosIdAccessorMap = CosIdAccessorSupport.getCosIdAccessor(NoGetterSetter.class);
        DefaultCosIdAccessor idAccessor = (DefaultCosIdAccessor) fieldCosIdAccessorMap.get(NoGetterSetter.class.getDeclaredField("id"));
        Assertions.assertNotNull(idAccessor);
        Assertions.assertTrue(idAccessor.getGetter() instanceof FieldGetter);
        Assertions.assertTrue(idAccessor.getSetter() instanceof FieldSetter);
    }

    @SneakyThrows
    @Test
    void finalId() {
        Map<Field, CosIdAccessor> fieldCosIdAccessorMap = CosIdAccessorSupport.getCosIdAccessor(FinalId.class);
        DefaultCosIdAccessor idAccessor = (DefaultCosIdAccessor) fieldCosIdAccessorMap.get(FinalId.class.getDeclaredField("id"));
        Assertions.assertNotNull(idAccessor);
        FinalId finalId = new FinalId();
        Assertions.assertEquals(0L, idAccessor.get(finalId));
        idAccessor.set(finalId, 1);
        Assertions.assertEquals(1L, idAccessor.get(finalId));
    }

    public static class WrongIdType {
        @CosId
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    public static class NoGetterSetter {
        @CosId
        private long id;
    }

    public static class FinalId {
        @CosId
        private final long id = 0;
    }
}
