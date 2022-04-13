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

package me.ahoo.cosid;

import me.ahoo.cosid.jvm.AtomicLongGenerator;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class IntegerIdGeneratorTest {
    
    @Test
    void generate() {
        IntegerIdGenerator idGen = new IntegerIdGenerator(AtomicLongGenerator.INSTANCE);
        int idFirst = idGen.generate();
        int idSecond = idGen.generate();
        Assertions.assertTrue(idSecond > idFirst);
    }
    
    @Test
    void generateWhenOverflow() {
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
        IntegerIdGenerator idGen = new IntegerIdGenerator(snowflakeId);
        Assertions.assertThrows(IntegerIdGenerator.IdOverflowException.class, () -> {
            try {
                idGen.generate();
            } catch (IntegerIdGenerator.IdOverflowException overflowException) {
                Assertions.assertTrue(overflowException.getId() > Integer.MAX_VALUE);
                throw overflowException;
            }
        });
    }
    
    @Test
    void generateAsString() {
        IntegerIdGenerator idGen = new IntegerIdGenerator(AtomicLongGenerator.INSTANCE);
        String idStr = idGen.generateAsString();
        Assertions.assertNotNull(idStr);
    }
}
