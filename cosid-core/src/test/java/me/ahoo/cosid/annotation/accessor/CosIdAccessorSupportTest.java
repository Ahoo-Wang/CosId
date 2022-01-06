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

package me.ahoo.cosid.annotation.accessor;

import lombok.SneakyThrows;
import me.ahoo.cosid.annotation.ChildEntity;
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.annotation.LongIdEntity;
import me.ahoo.cosid.annotation.accessor.field.FieldGetter;
import me.ahoo.cosid.annotation.accessor.field.FieldSetter;
import me.ahoo.cosid.annotation.accessor.method.MethodGetter;
import me.ahoo.cosid.annotation.accessor.method.MethodSetter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * @author ahoo wang
 */
class CosIdAccessorSupportTest {

    @SneakyThrows
    @Test
    void getCosIdAccessor() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor)CosIdAccessorSupport.getCosIdAccessor(LongIdEntity.class);

        Assertions.assertEquals(LongIdEntity.class.getDeclaredField("id"),cosIdAccessor.getIdField());

        Assertions.assertTrue(cosIdAccessor.getGetter() instanceof MethodGetter);
        Assertions.assertTrue(cosIdAccessor.getSetter() instanceof MethodSetter);
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
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) CosIdAccessorSupport.getCosIdAccessor(NoGetterSetter.class);

        Assertions.assertEquals(NoGetterSetter.class.getDeclaredField("id"),cosIdAccessor.getIdField());

        Assertions.assertTrue(cosIdAccessor.getGetter() instanceof FieldGetter);
        Assertions.assertTrue(cosIdAccessor.getSetter() instanceof FieldSetter);
    }

    @SneakyThrows
    @Test
    void finalId() {
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) CosIdAccessorSupport.getCosIdAccessor(FinalId.class);

        Assertions.assertEquals(FinalId.class.getDeclaredField("id"),cosIdAccessor.getIdField());

        FinalId finalId = new FinalId();
        Assertions.assertEquals(0L, cosIdAccessor.get(finalId));
        cosIdAccessor.set(finalId, 1);
        Assertions.assertEquals(1L, cosIdAccessor.get(finalId));
    }

    @SneakyThrows
    @Test
    void child(){
        DefaultCosIdAccessor cosIdAccessor = (DefaultCosIdAccessor) CosIdAccessorSupport.getCosIdAccessor(ChildEntity.class);
        Assertions.assertEquals(LongIdEntity.class.getDeclaredField("id"),cosIdAccessor.getIdField());
    }

    @Test
    void multipleId(){
        Assertions.assertThrows(MultipleIdNotSupportException.class, () -> {
            CosIdAccessorSupport.getCosIdAccessor(MultipleId.class);
        });
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

    public static class MultipleId {
        @CosId
        private  long id ;
        @CosId
        private  long id2 ;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public long getId2() {
            return id2;
        }

        public void setId2(long id2) {
            this.id2 = id2;
        }
    }
}
