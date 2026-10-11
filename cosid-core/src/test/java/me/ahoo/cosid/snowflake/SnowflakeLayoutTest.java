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

package me.ahoo.cosid.snowflake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.converter.Radix62IdConverter;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

class SnowflakeLayoutTest {
    
    @Test
    void derivesTheMaximaAndShifts() {
        SnowflakeLayout layout = new SnowflakeLayout(CosId.COSID_EPOCH, 41, 10, 12);
        
        assertEquals(~(-1L << 41), layout.maxTimestamp());
        assertEquals(1023, layout.maxMachineId());
        assertEquals(4095, layout.maxSequence());
        assertEquals(12, layout.machineLeft());
        assertEquals(22, layout.timestampLeft());
        assertFalse(layout.isSafeJavascript());
        assertTrue(new SnowflakeLayout(0, 31, 10, 12).isSafeJavascript());
    }
    
    @Test
    void rejectsTooManyOrNegativeBits() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeLayout(0, 42, 10, 12));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeLayout(0, -1, 10, 12));
    }
    
    @Test
    void generatorsAndDecoratorsExposeTheLayout() {
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
        SnowflakeId decorated = new StringSnowflakeId(new ClockSyncSnowflakeId(snowflakeId), Radix62IdConverter.INSTANCE);
        
        assertEquals(snowflakeId.layout(), decorated.layout());
        assertEquals(snowflakeId.getMaxSequence(), decorated.getMaxSequence());
        assertEquals(snowflakeId.getEpoch(), decorated.getEpoch());
    }
    
    @Test
    void parserIsBuiltFromTheLayout() {
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
        SnowflakeIdStateParser parser = SnowflakeIdStateParser.of(snowflakeId, ZoneId.of("UTC"), false);
        long id = snowflakeId.generate();
        
        assertEquals(id, parser.parse(parser.parse(id).getFriendlyId()).getId());
        assertEquals(1, parser.parse(id).getMachineId());
    }
}
