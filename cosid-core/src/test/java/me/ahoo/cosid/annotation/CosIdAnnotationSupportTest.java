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

package me.ahoo.cosid.annotation;

import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class CosIdAnnotationSupportTest {
    private IdGeneratorProvider idGeneratorProvider = DefaultIdGeneratorProvider.INSTANCE;
    private CosIdAnnotationSupport cosIdSupport = new CosIdAnnotationSupport(idGeneratorProvider);

    @Test
    void ensureId() {
        idGeneratorProvider.setShare(AtomicLongGenerator.INSTANCE);
        LongIdEntity entity = new LongIdEntity();
        cosIdSupport.ensureId(entity);
        Assertions.assertTrue(entity.getId()>0);
    }

    @Test
    void ensureChildId() {
        idGeneratorProvider.setShare(AtomicLongGenerator.INSTANCE);
        ChildEntity entity = new ChildEntity();
        cosIdSupport.ensureId(entity);
        Assertions.assertTrue(entity.getId()>0);
    }

    @Test
    void ensureIdExists() {
        idGeneratorProvider.setShare(AtomicLongGenerator.INSTANCE);
        long orderId=idGeneratorProvider.getShare().generate();
        LongIdEntity entity = new LongIdEntity();
        entity.setId(888L);
        cosIdSupport.ensureId(entity);
        Assertions.assertEquals(888, entity.getId());
    }

    @Test
    void ensureIdNotFindIdGen() {
        MissingIdGenEntity entity = new MissingIdGenEntity();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            cosIdSupport.ensureId(entity);
        });
    }



}
